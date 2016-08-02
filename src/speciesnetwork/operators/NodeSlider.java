package speciesnetwork.operators;

import java.util.ArrayList;
import java.util.List;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Operator;
import beast.core.parameter.IntegerParameter;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

import speciesnetwork.Network;
import speciesnetwork.NetworkNode;

/**
 * @author Chi Zhang
 */

@Description("Randomly selects an internal network node and move its height using an uniform sliding window.")
public class NodeSlider extends Operator {
    public Input<Network> speciesNetworkInput =
            new Input<>("speciesNetwork", "The species network.", Validate.REQUIRED);
    public Input<List<Tree>> geneTreesInput =
            new Input<>("geneTree", "list of gene trees embedded in species network", new ArrayList<>());
    public Input<List<IntegerParameter>> embeddingsInput =
            new Input<>("embedding", "The matrices to embed the gene trees in the species network.", new ArrayList<>());
    public Input<TaxonSet> taxonSuperSetInput =
            new Input<>("taxonSuperset", "Super-set of taxon sets mapping lineages to species.", Validate.REQUIRED);
    public Input<Double> windowSizeInput =
            new Input<>("windowSize", "The size of the sliding window, default 0.01.", 0.01);

    // empty constructor to facilitate construction by XML + initAndValidate
    public NodeSlider() {
    }

    @Override
    public void initAndValidate() {
    }

    /**
     * Propose a new network-node height from a uniform distribution.
     * If the new value is outside the boundary, the excess is reflected back into the interval.
     * The proposal ratio of this slider move is 1.0.
     * Then rebuild the embedding of the gene trees.
     */
    @Override
    public double proposal() {
        Network speciesNetwork = speciesNetworkInput.get();
        List<Tree> geneTrees = geneTreesInput.get();
        List<IntegerParameter> embeddings = embeddingsInput.get();
        final TaxonSet taxonSuperSet = taxonSuperSetInput.get();
        final double windowSize = windowSizeInput.get();

        // StringBuffer sb = new StringBuffer();
        // sb.append(speciesNetwork.toString());
        // sb.append("\n");
        // check the embedding in the current species network
        int oldChoices = 0;
        for (int ig = 0; ig < geneTrees.size(); ig++) {
            IntegerParameter embedding = embeddings.get(ig);
            Tree geneTree = geneTrees.get(ig);

            // print matrix for debugging
            /* sb = new StringBuffer();
            for (int i = 0; i < embedding.getMinorDimension2(); i++) {
                for (int j = 0; j < embedding.getMinorDimension1(); j++) {
                    sb.append(embedding.getMatrixValue(i, j));
                    sb.append("\t");
                }
                sb.append("\n");
            }
            sb.append(geneTree.getRoot().toNewick());
            sb.append("\n");
            sb.append(geneTree.getRoot().toString());
            System.out.println(sb); */

            RebuildEmbedding rebuildOperator = new RebuildEmbedding();
            rebuildOperator.initByName("speciesNetwork", speciesNetwork, "taxonSuperset", taxonSuperSet,
                    "geneTree", geneTree, "embedding", embedding);
            final int nChoices = rebuildOperator.getNumberOfChoices();
            oldChoices += nChoices;
            if(nChoices < 0)
                throw new RuntimeException("Developer ERROR: current embedding invalid! geneTree " + ig);
        }

        // pick an internal node randomly
        List<NetworkNode> intNodes = speciesNetwork.getInternalNodes();
        NetworkNode snNode = intNodes.get(Randomizer.nextInt(intNodes.size()));

        // determine the lower and upper bounds
        NetworkNode leftParent = snNode.getLeftParent();
        NetworkNode rightParent = snNode.getRightParent();
        double upper = Double.MAX_VALUE;
        if (leftParent != null)
            upper = leftParent.getHeight();
        if (rightParent != null && upper > rightParent.getHeight())
            upper = rightParent.getHeight();
        NetworkNode leftChild = snNode.getLeftChild();
        NetworkNode rightChild = snNode.getRightChild();
        double lower = Double.MIN_NORMAL;
        if (leftChild != null)
            lower = leftChild.getHeight();
        if (rightChild != null && lower < rightChild.getHeight())
            lower = rightChild.getHeight();
        if (lower >= upper)
            throw new RuntimeException("Developer ERROR: upper bound must be larger than lower bound!");

        // propose a new height, reflect it back if it's outside the boundary
        final double oldHeight = snNode.getHeight();
        double newHeight = oldHeight + (Randomizer.nextDouble() - 0.5) * windowSize;
        while (newHeight < lower || newHeight > upper) {
            if (newHeight < lower)
                newHeight = 2.0 * lower - newHeight;
            if (newHeight > upper)
                newHeight = 2.0 * upper - newHeight;
        }

        // update the new node height
        snNode.setHeight(newHeight);

        // sb = new StringBuffer();
        // sb.append(speciesNetwork.toString());  //sb.append("\n");
        // System.out.println(sb);
        // update the embedding in the new species network
        int newChoices = 0;
        for (int ig = 0; ig < geneTrees.size(); ig++) {
            IntegerParameter embedding = embeddings.get(ig);
            Tree geneTree = geneTrees.get(ig);

            // print matrix for debugging
            /* sb = new StringBuffer();
            for (int i = 0; i < embedding.getMinorDimension2(); i++) {
                for (int j = 0; j < embedding.getMinorDimension1(); j++) {
                    sb.append(embedding.getMatrixValue(i, j));
                    sb.append("\t");
                }
                sb.append("\n");
            }
            sb.append(geneTree.getRoot().toNewick());
            sb.append("\n");
            sb.append(geneTree.getRoot().toString());
            System.out.println(sb); */

            RebuildEmbedding rebuildOperator = new RebuildEmbedding();
            rebuildOperator.initByName("speciesNetwork", speciesNetwork, "taxonSuperset", taxonSuperSet,
                    "geneTree", geneTree, "embedding", embedding);
            // rebuild the embedding
            if (!rebuildOperator.initializeEmbedding())
                return Double.NEGATIVE_INFINITY;

            final int nChoices = rebuildOperator.getNumberOfChoices();
            newChoices += nChoices;
            if(nChoices < 0)
                throw new RuntimeException("Developer ERROR: new embedding invalid! geneTree " + ig);
        }

        return (newChoices - oldChoices) * Math.log(2);
    }
}
