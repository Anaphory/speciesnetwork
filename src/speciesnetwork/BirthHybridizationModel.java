package speciesnetwork;

import java.util.*;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.State;
import beast.core.parameter.RealParameter;
import beast.core.Distribution;
import beast.math.distributions.Beta;

/**
 * Birth hybridization model for the species network.
 * @author Chi Zhang
 */

@Description("Birth hybridization model (i.e. no death)")
public class BirthHybridizationModel extends Distribution {
    public final Input<Network> networkInput =
            new Input<>("network", "The species network.", Validate.REQUIRED);
    public final Input<RealParameter> birthRateInput =
            new Input<>("birthRate", "Speciation rate, lambda.");
    public final Input<RealParameter> hybridRateInput =
            new Input<>("hybridRate", "Hybridization rate, nu.");
    public final Input<RealParameter> netDiversification =
            new Input<>("netDiversification", "Net diversification rate: lambda-nu.");
    public final Input<RealParameter> turnOverInput =
            new Input<>("turnOver", "Turn over rate: nu/lambda.");
	public final Input<RealParameter> rhoProbInput = new Input<RealParameter>("rho",
			"Sampling prob. of extant species, rho.", new RealParameter(new Double[] { 1.0 }));
	public final Input<RealParameter> betaShapeInput = new Input<RealParameter>("betaShape",
			"Shape of the symmetric beta prior on gamma probs.", new RealParameter(new Double[] { 1.0 }));
    // public final Input<RealParameter> originInput =
    //        new Input<>("origin", "The time when the process started.");

    private static Comparator<NetworkNode> hc = new NodeHeightComparator();

    private double lambda, nu;
    private Beta betaPrior;

    private void recalculateParameters() {
	    if (birthRateInput.get() != null && hybridRateInput.get() != null) {
	        lambda = birthRateInput.get().getValue();
	        nu = hybridRateInput.get().getValue();
	    }
	    else if (netDiversification.get() != null && turnOverInput.get() != null) {
	        lambda = netDiversification.get().getValue() / (1 - turnOverInput.get().getValue());
	        nu = lambda * turnOverInput.get().getValue();
	    } else {
	        throw new RuntimeException("Either specify speciationRate and hybridizationRate " +
	                                    "OR specify netDiversification and turnOver.");
	    }
    }
    
    @Override
    public void initAndValidate() {
    	recalculateParameters();
        if (lambda <= 0.0 || nu <= 0.0) {
        	throw new RuntimeException("Speciation rate and hybridization rate must be positive!");
        }
        // rho = rhoProbInput.get() == null ? 1.0 : rhoProbInput.get().getValue();

        // make sure that all tips are at the same height, otherwise this Model is not appropriate
        final Network network = networkInput.get();
        final double firstHeight = network.nodes[0].height;
        for (int i = 1; i < network.leafNodeCount; i++) {
            final double height = network.nodes[i].height;
            if (Math.abs(firstHeight - height) > 1e-8) {
                throw new RuntimeException("Birth hybridization model cannot handle dated tips!");
            }
        }

        betaPrior = new Beta();
        final RealParameter betaShape;
        betaShape = betaShapeInput.get();
        betaPrior.alphaInput.setValue(betaShape, betaPrior);
        betaPrior.betaInput.setValue(betaShape, betaPrior);
    }

    @Override
    public double calculateLogP() {
        recalculateParameters();
        if (lambda <= 0.0 || nu <= 0.0) {
        	return Double.NEGATIVE_INFINITY;
        }
        
        final Network network = networkInput.get();

        // sort the internal nodes according to their heights in ascending order
        List<NetworkNode> nodes = Arrays.asList(network.getInternalNodesWithOrigin());
        nodes.sort(hc);

        logP = 0.0;
        // calculate probability of the network
        for (int i = 0; i < nodes.size(); i++) {
            final NetworkNode node = nodes.get(i);
            final double nodeHeight = node.getHeight();
            final double nextHeight;
            if (i == 0)  // the youngest internal node
                nextHeight = 0.0;  // the tip
            else
                nextHeight = nodes.get(i-1).getHeight();

            final int nBranch = network.getBranchCount((nodeHeight + nextHeight) /2.0);
            logP += (nBranch * lambda + nu * nBranch * (nBranch -1) /2) * (nextHeight - nodeHeight);

            if (node.isReticulation()) {
                logP += Math.log(nu);
                logP += betaPrior.logDensity(node.inheritProb);
            } else if (!node.isOrigin()) {
                logP += Math.log(lambda);
            }
        }

        return logP;
    }

    @Override
    protected boolean requiresRecalculation() {
        return networkInput.get().isDirty() ||
                (birthRateInput.get() != null && birthRateInput.get().somethingIsDirty()) ||
                (hybridRateInput.get() != null && hybridRateInput.get().somethingIsDirty()) ||
                (netDiversification.get() != null && netDiversification.get().somethingIsDirty()) ||
                (turnOverInput.get() != null && turnOverInput.get().somethingIsDirty());
    }

    @Override
    public List<String> getArguments() {
        return null;
    }

    @Override
    public List<String> getConditions() {
        return null;
    }

    @Override
    public void sample(State state, Random random) { }
}
