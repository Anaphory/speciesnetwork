package speciesnetwork.operators;

import java.util.List;

import beast.core.Description;
import beast.core.Input;
import beast.core.Operator;
import beast.util.Randomizer;
import speciesnetwork.Network;
import speciesnetwork.NetworkNode;

@Description("Changes the value of gamma by uniformly selecting a value in its range.")
public class GammaUniform extends Operator {
    public Input<Network> speciesNetworkInput = new Input<>("speciesNetwork", "The species network.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
    }

    @Override
    public double proposal() {
        final Network speciesNetwork = speciesNetworkInput.get();

        final List<NetworkNode> reticulationNodes = speciesNetwork.getReticulationNodes();
        final int nReticulations = reticulationNodes.size();
        final int randomNodeIndex = Randomizer.nextInt(nReticulations);
        final NetworkNode randomNode = reticulationNodes.get(randomNodeIndex);

        final Double newGamma = Randomizer.nextDouble();
        randomNode.setGamma(newGamma);

        return 0.0;
    }
}
