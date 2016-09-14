package speciesnetwork.operators;

import java.util.*;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Operator;
import beast.core.parameter.IntegerParameter;
import beast.evolution.alignment.Taxon;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;
import speciesnetwork.Network;
import speciesnetwork.NetworkNode;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * @author Huw Ogilvie
 * @author Chi Zhang
 */

@Description("Rebuild the embedding of a gene tree in the species network.")
public class RebuildEmbedding extends Operator {
    public Input<Network> speciesNetworkInput = new Input<>("speciesNetwork", "The species network.", Validate.REQUIRED);
    public Input<Tree> geneTreeInput = new Input<>("geneTree", "The gene tree.", Validate.REQUIRED);
    public Input<TaxonSet> taxonSuperSetInput =
            new Input<>("taxonSuperset", "Super-set of taxon sets mapping lineages to species.", Validate.REQUIRED);
    public Input<IntegerParameter> embeddingInput =
            new Input<>("embedding", "The matrix to embed the gene tree within the species network.", Validate.REQUIRED);

    // map of gene tree tip names to species network tip number  // do not map to nodes directly as the value may change
    private Map<String, Integer> geneTipMap;
    // heirs are the gene tree leaf tip numbers below each gene tree node or species network node
    private Multimap<Node, Integer> geneNodeHeirs;
    private Multimap<NetworkNode, Integer> speciesNodeHeirs;

    private Tree geneTree;
    private Network speciesNetwork;
    private IntegerParameter embedding;

    @Override
    public void initAndValidate() {
        speciesNetwork = speciesNetworkInput.get();
        geneTipMap = new HashMap<>();
        geneNodeHeirs = HashMultimap.create();
        speciesNodeHeirs = HashMultimap.create();

        final NetworkNode[] speciesLeafNodes = speciesNetwork.getLeafNodes();
        // generate map of species network tip names to species network tip nodes
        final Map<String, NetworkNode> speciesNodeMap = new HashMap<>();
        for (NetworkNode leafNode: speciesLeafNodes) {
            final String speciesName = leafNode.getLabel();
            speciesNodeMap.put(speciesName, leafNode);
        }
        // generate map of gene tree tip names to species network tip numbers
        final TaxonSet taxonSuperSet = taxonSuperSetInput.get();
        for (Taxon species: taxonSuperSet.taxonsetInput.get()) {
            final String speciesName = species.getID();
            final NetworkNode speciesNode = speciesNodeMap.get(speciesName);
            final TaxonSet speciesTaxonSet = (TaxonSet) species;
            for (Taxon geneTip: speciesTaxonSet.taxonsetInput.get()) {
                final String gTipName = geneTip.getID();
                geneTipMap.put(gTipName, speciesNode.getNr());
            }
        }
    }

    @Override
    public double proposal() {
        geneTree = geneTreeInput.get();
        speciesNetwork = speciesNetworkInput.get();
        embedding = embeddingInput.get();
        // count the number of alternative traversing choices for the current state (n0)
        getNodeHeirs();
        final int oldChoices = recurseRebuild(geneTree.getRoot(), speciesNetwork.getRoot(), false);
        if (oldChoices < 0)
            throw new RuntimeException("Developer ERROR: current embedding invalid!");

        // rebuild the embedding AND
        // count the number of alternative traversing choices for the new state (n1)
        resetEmbedding();
        final int newChoices = recurseRebuild(geneTree.getRoot(), speciesNetwork.getRoot(), true);
        if (newChoices < 0)
            return Double.NEGATIVE_INFINITY;

        // the proposal ratio is (2^n1)/(2^n0)
        return (newChoices - oldChoices) * Math.log(2);
    }

    public int initializeEmbedding() {
        geneTree = geneTreeInput.get();
        speciesNetwork = speciesNetworkInput.get();
        embedding = embeddingInput.get();
        getNodeHeirs();
        // rebuild the embedding
        resetEmbedding();
        return recurseRebuild(geneTree.getRoot(), speciesNetwork.getRoot(), true);
    }

    protected int getNumberOfChoices() {
        geneTree = geneTreeInput.get();
        speciesNetwork = speciesNetworkInput.get();
        embedding = embeddingInput.get();
        getNodeHeirs();
        return recurseRebuild(geneTree.getRoot(), speciesNetwork.getRoot(), false);
    }

    private void resetEmbedding() {
        // nothing traverses through species leaf nodes
        final int traversalNodeCount = speciesNetwork.getTraversalNodeCount();
        final int geneNodeCount = geneTree.getNodeCount();

        embedding.setDimension(traversalNodeCount * geneNodeCount);
        embedding.setMinorDimension(geneNodeCount);

        // initialize embedding matrix to -1 (no traversal)
        for (int i = 0; i < traversalNodeCount; i++) {
            for (int j = 0; j < geneNodeCount; j++) {
                embedding.setMatrixValue(i, j, -1);
            }
        }
    }

    private void getNodeHeirs() {
        geneNodeHeirs.clear();
        speciesNodeHeirs.clear();

        for (final Node geneLeaf: geneTree.getExternalNodes()) {
            final int leafNodeNumber = geneLeaf.getNr();
            final String tipName = geneLeaf.getID();
            final int speciesNodeNumber = geneTipMap.get(tipName);
            final NetworkNode speciesNetworkNode = speciesNetwork.getNode(speciesNodeNumber);
            speciesNodeHeirs.put(speciesNetworkNode, leafNodeNumber);
            recurseGeneHeirs(geneLeaf, leafNodeNumber);
        }

        for (final NetworkNode speciesLeaf: speciesNetwork.getLeafNodes()) {
            recurseSpeciesHeirs(speciesLeaf);
        }
    }

    // the heir for each gene leaf node is itself
    // the heirs for each species leaf node is the associated gene leaf nodes
    private void recurseGeneHeirs(final Node geneTreeNode, final int leafNodeNumber) {
        geneNodeHeirs.put(geneTreeNode, leafNodeNumber);

        final Node parentNode = geneTreeNode.getParent();
        if (parentNode != null) recurseGeneHeirs(parentNode, leafNodeNumber);
    }

    private void recurseSpeciesHeirs(final NetworkNode speciesNetworkNode) {
        for (NetworkNode c: speciesNetworkNode.getChildren()) {
            speciesNodeHeirs.putAll(speciesNetworkNode, speciesNodeHeirs.get(c));
        }

        for (NetworkNode parentNode: speciesNetworkNode.getParents()) {
            // System.out.println(String.format("%s -> %s", speciesNetworkNode.getLabel(), p.getLabel()));
            recurseSpeciesHeirs(parentNode);
        }
    }

    private int recurseRebuild(final Node geneTreeNode, final NetworkNode speciesNetworkNode, boolean rebuild) {
        final double geneTreeNodeHeight = geneTreeNode.getHeight();
        final double speciesNetworkNodeHeight = speciesNetworkNode.getHeight();
        int nChoices = 0;

        // this coalescence node must be embedded in a descendant species network branch
        if (geneTreeNodeHeight < speciesNetworkNodeHeight) {
            final int traversalNodeNumber = speciesNetworkNode.getTraversalNumber();
            final int geneTreeNodeNumber = geneTreeNode.getNr();
            final Collection<Integer> requiredHeirs = geneNodeHeirs.get(geneTreeNode);
            final List<Integer> compatibleSpeciesBranches = new ArrayList<>();

            for (Integer branchNumber: speciesNetworkNode.childBranchNumbers) {
                final NetworkNode childSpeciesNode = speciesNetworkNode.getChildByBranch(branchNumber);
                if (speciesNodeHeirs.get(childSpeciesNode).containsAll(requiredHeirs)) {
                    compatibleSpeciesBranches.add(branchNumber);
                }
            }
            if (compatibleSpeciesBranches.size() == 0) {
                return -1; // for a valid embedding, should never go here
            } else if (compatibleSpeciesBranches.size() > 1) {
                nChoices++;
            }

            Integer nextSpeciesBranchNumber;
            if (rebuild) {
                final int nextBranchIndex = Randomizer.nextInt(compatibleSpeciesBranches.size());
                nextSpeciesBranchNumber = compatibleSpeciesBranches.get(nextBranchIndex);
                embedding.setMatrixValue(traversalNodeNumber, geneTreeNodeNumber, nextSpeciesBranchNumber);
            } else {
                nextSpeciesBranchNumber = embedding.getMatrixValue(traversalNodeNumber, geneTreeNodeNumber);
            }
            assert (nextSpeciesBranchNumber >= 0);
            final NetworkNode nextSpecies = speciesNetworkNode.getChildByBranch(nextSpeciesBranchNumber);
            final int moreChoices = recurseRebuild(geneTreeNode, nextSpecies, rebuild);
            if (moreChoices < 0) return -1;

            return nChoices + moreChoices;
        } else if (geneTreeNode.isLeaf()) {
            return 0;
        } else {
            // embed both gene tree children
            final int leftChoices = recurseRebuild(geneTreeNode.getLeft(), speciesNetworkNode, rebuild);
            if (leftChoices < 0) return -1;
            final int rightChoices = recurseRebuild(geneTreeNode.getRight(), speciesNetworkNode, rebuild);
            if (rightChoices < 0) return -1;

            return nChoices + leftChoices + rightChoices;
        }
    }
    
    protected void printEmbedding() {
        embedding = embeddingInput.get();
        speciesNetwork = speciesNetworkInput.get();
        geneTree = geneTreeInput.get();
        StringBuffer embedStr = new StringBuffer();
        final int nTraversalNodes = speciesNetwork.getTraversalNodeCount();
        final int nGeneNodes = geneTree.getNodeCount();
        for (int i = 0; i < nTraversalNodes; i++) {
            final int speciesNodeNumber = i + speciesNetwork.getLeafNodeCount();
            embedStr.append(String.format("%3s", speciesNetwork.getNode(speciesNodeNumber).getLabel()));
            for (int j = 0; j < nGeneNodes; j++) {
                embedStr.append(String.format("%3s", embedding.getMatrixValue(i, j)));
            }
            embedStr.append("\n");
        }
        System.out.print(embedStr);
    }
}
