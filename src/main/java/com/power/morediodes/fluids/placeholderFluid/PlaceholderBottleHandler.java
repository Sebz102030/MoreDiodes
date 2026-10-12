public class PlaceholderBottleHandler implements IFluidHandlerItem {
    public static final int VOLUME = 250;
    private ItemStack container;

    public PlaceholderBottleHandler(ItemStack container) { this.container = container; }

    private boolean isFull() { return container.is(ModFluids.PLACEHOLDER_BOTTLE.get()); }
    private static boolean valid(FluidStack s) { return s.getFluid().isSame(ModFluids.PLACEHOLDER.get()); }

    @Override public ItemStack getContainer() { return container; }
    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int tank) {
        return isFull() ? new FluidStack(ModFluids.PLACEHOLDER.get(), VOLUME) : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank) { return VOLUME; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) { return valid(stack); }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (container.getCount() != 1 || isFull() || !container.is(Items.GLASS_BOTTLE)
                || !valid(resource) || resource.getAmount() < VOLUME)
            return 0;
        if (action.execute())
            container = new ItemStack(ModFluids.PLACEHOLDER_BOTTLE.get());
        return VOLUME;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return valid(resource) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (container.getCount() != 1 || !isFull() || maxDrain < VOLUME)
            return FluidStack.EMPTY;
        if (action.execute())
            container = new ItemStack(Items.GLASS_BOTTLE);
        return new FluidStack(ModFluids.PLACEHOLDER.get(), VOLUME);
    }
}