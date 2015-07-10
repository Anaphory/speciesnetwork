package starbeast2;


import java.util.ArrayList;
import java.util.List;

import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.StateNode;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Node;
import beast.evolution.tree.TreeInterface;

/**
* @author Huw Ogilvie
 */

public class LinearPopulation extends MultispeciesPopulationModel {
    public Input<RealParameter> topPopSizesInput = new Input<RealParameter>("topPopSizes", "Population sizes at the top (rootward) end of each branch.", Validate.REQUIRED);
    public Input<RealParameter> tipPopSizesInput = new Input<RealParameter>("tipPopSizes", "Population sizes at the tips of leaf branches.", Validate.REQUIRED);

    private RealParameter topPopSizes;
    private RealParameter tipPopSizes;
    
    private int nBranches;
    private int nSpecies;
    private TreeInterface speciesTreeRef;

    @Override
    public void initAndValidate() throws Exception {
        topPopSizes = topPopSizesInput.get();
        tipPopSizes = tipPopSizesInput.get();
    }

    @Override
    public double branchLogP(int speciesTreeNodeNumber, double[] perGenePloidy, List<Double[]> branchCoalescentTimes, int[] branchLineageCounts, int[] branchEventCounts) {
        final int nGenes = perGenePloidy.length;
        final Node speciesTreeNode = speciesTreeRef.getNode(speciesTreeNodeNumber);

        final double branchTopPopSize = topPopSizes.getValue(speciesTreeNodeNumber);

        double branchTipPopSize;
        if (speciesTreeNode.isLeaf()) {
            branchTipPopSize = tipPopSizes.getValue(speciesTreeNodeNumber);
        } else {
            final int leftChildNodeNumber = speciesTreeNode.getLeft().getNr();
            final int rightChildNodeNumber = speciesTreeNode.getRight().getNr();
            branchTipPopSize = topPopSizes.getValue(leftChildNodeNumber) + topPopSizes.getValue(rightChildNodeNumber);
        }

        // set the root branch heights for each gene tree (to equal the highest gene tree root node)
        double tallestGeneTreeHeight = 0.0;
        if (speciesTreeNode.isRoot()) {
            for (int j = 0; j < nGenes; j++) {
                tallestGeneTreeHeight = Math.max(tallestGeneTreeHeight, branchCoalescentTimes.get(j)[branchEventCounts[j]]);
            }
            for (int j = 0; j < nGenes; j++) {
                branchCoalescentTimes.get(j)[branchEventCounts[j] + 1] = tallestGeneTreeHeight;
            }
        }

        final double logP = linearLogP(branchTopPopSize, branchTipPopSize, perGenePloidy, branchCoalescentTimes, branchLineageCounts, branchEventCounts);

        /*// for debugging
        if (speciesTreeNode.isRoot()) {
            System.out.println(String.format("Tallest gene tree height = %f", tallestGeneTreeHeight));
            System.out.println(String.format("Root node %d logP = %f", speciesTreeNodeNumber, logP));
        } else if (speciesTreeNode.isLeaf()) {
            System.out.println(String.format("Leaf node %d logP = %f", speciesTreeNodeNumber, logP));
        } else { 
            System.out.println(String.format("Internal node %d logP = %f", speciesTreeNodeNumber, logP));
        }
        */

        return logP;
    }

    @Override
    public List<StateNode> initializePopSizes(TreeInterface speciesTree, double popInitial) {
        speciesTreeRef = speciesTree;
        nBranches = speciesTree.getNodeCount();
        nSpecies = speciesTree.getLeafNodeCount();
        final List<StateNode> popSizeVectors = new ArrayList<StateNode>();

        topPopSizes.setDimension(nBranches);
        tipPopSizes.setDimension(nSpecies);

        popSizeVectors.add(topPopSizes);
        popSizeVectors.add(tipPopSizes);

        for (int i = 0; i < topPopSizes.getDimension(); i++) {
            topPopSizes.setValue(i, popInitial / 2.0);
        }

        for (int i = 0; i < tipPopSizes.getDimension(); i++) {
            tipPopSizes.setValue(i, popInitial);
        }

        return popSizeVectors;
    }

    @Override
    public String serialize(Node speciesTreeNode) {
        final int speciesTreeNodeNumber = speciesTreeNode.getNr();

        final double branchTopPopSize = topPopSizes.getValue(speciesTreeNodeNumber);

        double branchTipPopSize;
        if (speciesTreeNode.isLeaf()) {
            branchTipPopSize = tipPopSizes.getValue(speciesTreeNodeNumber);
        } else {
            final int leftChildNodeNumber = speciesTreeNode.getLeft().getNr();
            final int rightChildNodeNumber = speciesTreeNode.getRight().getNr();
            branchTipPopSize = topPopSizes.getValue(leftChildNodeNumber) + topPopSizes.getValue(rightChildNodeNumber);
        }

        final String dmv = String.format("dmv={%f,%f}", branchTopPopSize, branchTipPopSize);

        return dmv;
    }
}