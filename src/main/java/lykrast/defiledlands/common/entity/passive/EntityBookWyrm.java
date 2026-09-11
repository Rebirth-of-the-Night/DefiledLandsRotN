package lykrast.defiledlands.common.entity.passive;

import java.util.*;

import javax.annotation.Nullable;

import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import io.netty.buffer.ByteBuf;
import lykrast.defiledlands.common.entity.IEntityDefiled;
import lykrast.defiledlands.common.init.ModBlocks;
import lykrast.defiledlands.common.init.ModItems;
import lykrast.defiledlands.common.init.ModSounds;
import lykrast.defiledlands.common.util.Config;
import lykrast.defiledlands.core.DefiledLands;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIFollowParent;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMate;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import static net.minecraft.item.Item.getByNameOrId;


//I'm Booking It
public class EntityBookWyrm extends EntityAnimal implements IEntityDefiled, IEntityAdditionalSpawnData {
    public static final ResourceLocation LOOT = new ResourceLocation(DefiledLands.MODID, "entities/bookwyrm/normal");
    public static final ResourceLocation LOOT_GOLDEN = new ResourceLocation(DefiledLands.MODID, "entities/bookwyrm/golden");
    private static final ResourceLocation TRADES = new ResourceLocation(DefiledLands.MODID, "misc/normal_trades");
    private static final ResourceLocation GOLDEN_TRADES = new ResourceLocation(DefiledLands.MODID, "misc/golden_trades");
    private static final Set<Item> TEMPTATION_ITEMS = Sets.newHashSet(Items.ENCHANTED_BOOK, ModItems.foulCandy);
    private static final DataParameter<Boolean> GOLDEN = EntityDataManager.<Boolean>createKey(EntityBookWyrm.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> THROW_ITEM_PLAYER = EntityDataManager.createKey(EntityBookWyrm.class, DataSerializers.BOOLEAN);

    private EntityPlayer selectedPlayer = null;
    private int traderCooldown = 0;
    private long lootTableSeed;

    public EntityBookWyrm(World worldIn) {
        super(worldIn);
        this.setSize(0.9F, 0.9F);
        this.spawnableBlock = ModBlocks.grassDefiled;
    }

    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(GOLDEN, Boolean.valueOf(false));
        this.dataManager.register(THROW_ITEM_PLAYER, Boolean.valueOf(false));
    }

    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new AIPanic(1.2D));
        this.tasks.addTask(2, new EntityAILeapAtTarget(this, 0.4F));
        this.tasks.addTask(3, new EntityAIAttackMelee(this, 1.2D, false));
        this.tasks.addTask(4, new EntityAIMate(this, 1.0D));
        this.tasks.addTask(5, new EntityAITempt(this, 1.2D, false, TEMPTATION_ITEMS));
        this.tasks.addTask(6, new EntityAIFollowParent(this, 1.1D));
        this.tasks.addTask(7, new EntityAIWanderAvoidWater(this, 1.0D));
        this.tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
        this.tasks.addTask(9, new EntityAILookIdle(this));
        this.targetTasks.addTask(1, new AIHurtByTarget());
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(12.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.26D);
    }

    public boolean attackEntityAsMob(Entity entityIn) {
        return entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), 5.0F);
    }

    protected void updateAITasks() {
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            if (this.getAttackTarget() != null) this.setAttackTarget(null);
            if (this.getRevengeTarget() != null) this.setRevengeTarget(null);
        }

        super.updateAITasks();
    }

    /**
     * Called frequently so the entity can update its state every tick as required. For example, zombies and skeletons
     * use this to react to sunlight and start to burn.
     */
    public void onLivingUpdate() {
        super.onLivingUpdate();
        traderCooldown--;
        if(!world.isRemote) {
            selectedPlayer = this.world.getClosestPlayer(this.posX, this.posY, this.posZ, 5, Predicates.and(EntitySelectors.NOT_SPECTATING, EntitySelectors.notRiding(this)));
            //allows trading
            if(selectedPlayer != null && traderCooldown < 0) {
                //disables the player trading if they get out of trading distance
                ItemStack playerStack = selectedPlayer.getHeldItemMainhand();
                Vec3d playerPos = selectedPlayer.getPositionVector();
                this.getLookHelper().setLookPosition(playerPos.x, playerPos.y + selectedPlayer.getEyeHeight(), playerPos.z, 30, 30);
            }
        }
    }

    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && selectedPlayer != null && !this.isThrowItemPlayer() && traderCooldown < 0) {
            boolean checkItem = false;
            for(String s : Config.bookWyrmCurrency){
                if(getByNameOrId(s).equals(stack.getItem())) {
                    checkItem = true;
                    break;
                }
            }
            if (checkItem) {
                stack.shrink(1);
                this.throwItemAfterTrade(getTradeTable(), selectedPlayer);
            }
        }
        return super.processInteract(player, hand);
    }

    private void setThrowItemPlayer(boolean value) {this.dataManager.set(THROW_ITEM_PLAYER, Boolean.valueOf(value));}
    private boolean isThrowItemPlayer() {return this.dataManager.get(THROW_ITEM_PLAYER);}

    public static Vec3d yVec(double heightAboveGround) {
        return new Vec3d(0, heightAboveGround, 0);
    }

    public Vec3d getRelativeOffset(EntityLivingBase actor, Vec3d offset) {
        Vec3d look = getVectorForRotation(0, actor.renderYawOffset);
        Vec3d side = look.rotateYaw((float) Math.PI * 0.5f);
        return look.scale(offset.x).add(yVec((float) offset.y)).add(side.scale(offset.z));
    }

    public void shoot(Entity entityIn, double x, double y, double z, float velocity, float inaccuracy)
    {
        float f = MathHelper.sqrt(x * x + y * y + z * z);
        x = x / (double)f;
        y = y / (double)f;
        z = z / (double)f;
        x = x + this.rand.nextGaussian() * 0.007499999832361937D * (double)inaccuracy;
        y = y + this.rand.nextGaussian() * 0.007499999832361937D * (double)inaccuracy;
        z = z + this.rand.nextGaussian() * 0.007499999832361937D * (double)inaccuracy;
        x = x * (double)velocity;
        y = y * (double)velocity;
        z = z * (double)velocity;
        entityIn.motionX = x;
        entityIn.motionY = y;
        entityIn.motionZ = z;
        float f1 = MathHelper.sqrt(x * x + z * z);
        entityIn.rotationYaw = (float)(MathHelper.atan2(x, z) * (180D / Math.PI));
        entityIn.rotationPitch = (float)(MathHelper.atan2(y, (double)f1) * (180D / Math.PI));
        entityIn.prevRotationYaw = entityIn.rotationYaw;
        entityIn.prevRotationPitch = entityIn.rotationPitch;
    }



    private void throwItemAfterTrade(ItemStack stack, EntityPlayer player) {
        this.setThrowItemPlayer(true);
        //throws item in hand too player
        EntityItem itemToThrow = new EntityItem(world, this.posX, this.posY + this.getEyeHeight() - 0.1, this.posZ, stack);
        Vec3d lookScale = player.getPositionVector();
        Vec3d relPos = this.getPositionVector().add(getRelativeOffset(this, new Vec3d(1, 1.6, 0)));
        double d0 = lookScale.y + (double)player.getEyeHeight() - 1.100000023841858D;
        double d1 = lookScale.x - relPos.x;
        double d2 = d0 - this.posY;
        double d3 = lookScale.z - relPos.z;
        float f = MathHelper.sqrt(d1 * d1 + d3 * d3);
        this.shoot(itemToThrow, d1, d2 + (double)(f * 0.1F), d3, 0.3F, 1.0F);
        itemToThrow.velocityChanged = true;
        // itemToThrow.addVelocity(lookScale.x, lookScale.y, lookScale.z);
        world.spawnEntity(itemToThrow);
        traderCooldown = 20;
        this.setThrowItemPlayer(false);
    }

    private List<ItemStack> trade_items = Lists.newArrayList();
    private List<ItemStack> golden_trade_items = Lists.newArrayList();

    protected ItemStack getTradeTable() {
        if(!world.isRemote) {
            LootContext.Builder lootcontext$builder = (new LootContext.Builder((WorldServer) this.world)).withLootedEntity(this);
            if(this.isGolden()){
                golden_trade_items = this.world.getLootTableManager().getLootTableFromLocation(GOLDEN_TRADES).generateLootForPools(this.lootTableSeed == 0 ? new Random() :new Random(this.lootTableSeed), lootcontext$builder.build());
                for (ItemStack item : golden_trade_items) {
                    return item;
                }
            }
            else{
                trade_items = this.world.getLootTableManager().getLootTableFromLocation(TRADES).generateLootForPools(this.lootTableSeed == 0 ? new Random() :new Random(this.lootTableSeed), lootcontext$builder.build());
                for (ItemStack item : trade_items) {
                    return item;
                }
            }
        }
        return ItemStack.EMPTY;
    }


    /**
     * Called only once on an entity when first time spawned, via egg, mob spawner, natural spawning etc, but not called
     * when entity is reloaded from nbt. Mainly used for initializing attributes and inventory
     */
    @Nullable
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData livingdata) {
        livingdata = super.onInitialSpawn(difficulty, livingdata);

        setGolden(world.rand.nextInt(100) == 0);

        if (this.rand.nextInt(5) == 0) {
            this.setGrowingAge(-24000);
        }

        return livingdata;
    }

    @Override
    public EntityAgeable createChild(EntityAgeable ageable) {
        EntityBookWyrm child = new EntityBookWyrm(world);

        if (ageable instanceof EntityBookWyrm) setOffspringAttributes((EntityBookWyrm) ageable, child);

        return child;
    }

    protected void setOffspringAttributes(EntityBookWyrm parent, EntityBookWyrm child) {
        //Golden
        boolean flag1 = isGolden();
        boolean flag2 = parent.isGolden();

        if (flag1 || flag2) {
            int i = 25;
            if (flag1 && flag2) i = 10;

            child.setGolden(rand.nextInt(i) == 0);
        } else child.setGolden(rand.nextInt(100) == 0);
    }

    /**
     * (abstract) Protected helper method to write subclass entity data to NBT.
     */
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setBoolean("Golden", this.isGolden());
        compound.setLong("Table_Seed", lootTableSeed);
        compound.setBoolean("Throw_Item", this.isThrowItemPlayer());
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        setGolden(compound.getBoolean("Golden"));
        this.setThrowItemPlayer(compound.getBoolean("Throw_Item"));
        this.lootTableSeed = compound.getLong("Table_Seed");
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {

    }

    @Override
    public void readSpawnData(ByteBuf additionalData) {

    }

    protected SoundEvent getAmbientSound() {
        return ModSounds.bookWyrmIdle;
    }

    protected SoundEvent getHurtSound(DamageSource p_184601_1_) {
        return ModSounds.bookWyrmHurt;
    }

    protected SoundEvent getDeathSound() {
        return ModSounds.bookWyrmDeath;
    }

    protected void playStepSound(BlockPos pos, Block blockIn) {
        this.playSound(SoundEvents.ENTITY_PIG_STEP, 0.15F, 1.0F);
    }

    public boolean isGolden() {
        return dataManager.get(GOLDEN);
    }

    public void setGolden(boolean golden) {
        dataManager.set(GOLDEN, golden);
    }

    @Override
    @Nullable
    protected ResourceLocation getLootTable() {
        if (isGolden()) return LOOT_GOLDEN;
        else return LOOT;
    }

    /**
     * Checks if the parameter is an item which this animal can be fed to breed it (wheat, carrots or seeds depending on
     * the animal type)
     */
    public boolean isBreedingItem(ItemStack stack) {
        return stack.getItem() == ModItems.foulCandy;
    }

    //-------------------------
    //Borrowed from polar bears
    //-------------------------
    class AIHurtByTarget extends EntityAIHurtByTarget {
        public AIHurtByTarget() {
            super(EntityBookWyrm.this, true);
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            super.startExecuting();

            if (EntityBookWyrm.this.isChild()) {
                this.alertOthers();
                this.resetTask();
            }
        }

        protected void setEntityAttackTarget(EntityCreature creatureIn, EntityLivingBase entityLivingBaseIn) {
            if (creatureIn instanceof EntityBookWyrm && !creatureIn.isChild()) {
                super.setEntityAttackTarget(creatureIn, entityLivingBaseIn);
            }
        }

        public boolean shouldExecute() {
            if (this.taskOwner.world.getDifficulty() == EnumDifficulty.PEACEFUL) return false;
            else return super.shouldExecute();
        }
    }

    class AIPanic extends EntityAIPanic {
        public AIPanic(double speed) {
            super(EntityBookWyrm.this, speed);
        }

        /**
         * Returns whether the EntityAIBase should begin execution.
         */
        public boolean shouldExecute() {
            return !EntityBookWyrm.this.isChild() && !EntityBookWyrm.this.isBurning() ? false : super.shouldExecute();
        }
    }
}