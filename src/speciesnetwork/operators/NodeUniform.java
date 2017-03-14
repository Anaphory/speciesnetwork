package speciesnetwork.operators;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Operator;
import beast.util.Randomizer;
import speciesnetwork.Network;
import speciesnetwork.NetworkNode;
import speciesnetwork.SanityChecks;

/**
 * Randomly pick an internal network node (excluding origin).
 * Change its height uniformly between the lower and upper limit.
 *
 * @author Chi Zhang
 */

@Description("Randomly select an internal network node and move its height uniformly.")
public class NodeUniform extends Operator {
    public final Input<Network> speciesNetworkInput =
            new Input<>("speciesNetwork", "The species network.", Validate.REQUIRED);

    protected double upper, lower, oldHeight, newHeight;
    protected NetworkNode snNode;

    @Override
    public void initAndValidate() {
    }

    @Override
    public double proposal() {
        final Network speciesNetwork = speciesNetworkInput.get();

        // pick an internal node randomly
        final NetworkNode[] internalNodes = speciesNetwork.getInternalNodes();
        final int randomIndex = Randomizer.nextInt(internalNodes.length);
        snNode = internalNodes[randomIndex];

        // determine the lower and upper bounds
        upper = Double.MAX_VALUE;
        for (NetworkNode p: snNode.getParents()) {
            upper = Math.min(upper, p.getHeight());
        }
        lower = 0.0;
        for (NetworkNode c: snNode.getChildren()) {
            lower = Math.max(lower, c.getHeight());
        }

        // propose a new height uniformly
        oldHeight = snNode.getHeight();
        newHeight = Randomizer.nextDouble() * (upper - lower) + lower;
        speciesNetwork.startEditing(this);
        snNode.setHeight(newHeight);
        SanityChecks.checkNetworkSanity(speciesNetwork.getOrigin());

        return 0.0;
    }
}
