public class PlaceholderBucketItem extends BucketItem {
    public PlaceholderBucketItem(Fluid fluid, Properties properties) { super(fluid, properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand)); // no block, so never place it
    }
}