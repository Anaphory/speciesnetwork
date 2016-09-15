package speciesnetwork.operators;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Operator;
import beast.util.Randomizer;
import speciesnetwork.Network;
import speciesnetwork.NetworkNode;

@Description("Changes the value of gamma by applying a random walk to the logit of gamma.")
public class GammaProbRandomWalk extends Operator {
    public Input<Network> speciesNetworkInput = new Input<>("speciesNetwork", "The species network.", Validate.REQUIRED);
    public Input<Double> windowSizeInput = new Input<>("windowSize", "The size of the sliding window, default 1.0.", 1.0);

    @Override
    public void initAndValidate() {
    }

    @Override
    public double proposal() {
        final Network speciesNetwork = speciesNetworkInput.get();
        final Double windowSize = windowSizeInput.get();

        speciesNetwork.startEditing(this);

        final int nReticulations = speciesNetwork.getReticulationNodeCount();
        final int randomNodeIndex = Randomizer.nextInt(nReticulations) + speciesNetwork.getReticulationOffset();
        final NetworkNode randomNode = speciesNetwork.getNode(randomNodeIndex);

        final Double logOddsShift = (Randomizer.nextDouble() * windowSize * 2) - windowSize;
        final Double currentGamma = randomNode.getGammaProb();
        final Double currentLogOdds = Math.log(currentGamma / (1.0 - currentGamma));
        final Double newLogOdds = currentLogOdds + logOddsShift;
        final Double newGamma = 1.0 / (1.0 + Math.exp(-newLogOdds));
        randomNode.setGammaProb(newGamma);

        return 0.0;
    }
}
