package flaxbeard.immersivepetroleum.common;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent.OverlayType;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.ManualPageMultiblock;
import blusunrize.immersiveengineering.api.MultiblockHandler.IMultiblock;
import blusunrize.immersiveengineering.api.tool.ExcavatorHandler;
import blusunrize.immersiveengineering.client.ClientProxy;
import blusunrize.immersiveengineering.client.ClientUtils;
import blusunrize.immersiveengineering.common.Config.IEConfig;
import blusunrize.immersiveengineering.common.IEContent;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IBlockOverlayText;
import blusunrize.immersiveengineering.common.blocks.TileEntityMultiblockPart;
import blusunrize.immersiveengineering.common.blocks.metal.BlockTypes_MetalDevice1;
import blusunrize.immersiveengineering.common.blocks.metal.TileEntitySampleDrill;
import blusunrize.immersiveengineering.common.blocks.stone.TileEntityCoresample;
import blusunrize.immersiveengineering.common.items.ItemCoresample;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.lib.manual.IManualPage;
import blusunrize.lib.manual.ManualInstance;
import blusunrize.lib.manual.ManualInstance.ManualEntry;
import blusunrize.lib.manual.gui.GuiManual;
import flaxbeard.immersivepetroleum.api.crafting.LubricatedHandler;
import flaxbeard.immersivepetroleum.api.crafting.LubricatedHandler.ILubricationHandler;
import flaxbeard.immersivepetroleum.api.crafting.LubricatedHandler.LubricatedTileInfo;
import flaxbeard.immersivepetroleum.api.crafting.PumpjackHandler;
import flaxbeard.immersivepetroleum.api.crafting.PumpjackHandler.OilWorldInfo;
import flaxbeard.immersivepetroleum.api.crafting.PumpjackHandler.ReservoirType;
import flaxbeard.immersivepetroleum.common.Config.IPConfig;
import flaxbeard.immersivepetroleum.common.entity.EntitySpeedboat;
import flaxbeard.immersivepetroleum.common.network.CloseBookPacket;
import flaxbeard.immersivepetroleum.common.network.IPPacketHandler;
import flaxbeard.immersivepetroleum.common.network.MessageReservoirListSync;

public class EventHandler
{
	@SubscribeEvent
	public void onSave(WorldEvent.Save event)
	{
		IPSaveData.setDirty(0);
	}
	
	@SubscribeEvent
	public void onUnload(WorldEvent.Unload event)
	{
		IPSaveData.setDirty(0);
	}
	
	private Object lastGui = null;
	
	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void guiOpen(GuiOpenEvent event)
	{
		if (event.getGui() == null && lastGui instanceof GuiManual)
		{
			GuiManual gui = (GuiManual) lastGui;
			String name = null;

			ManualInstance inst = gui.getManual();
			if (inst != null)
			{
				ManualEntry entry = inst.getEntry(gui.getSelectedEntry());
				if (entry != null)
				{
					IManualPage[] pages = entry.getPages();
					for (int i = 0; i < pages.length; i++)
					{
						IManualPage page = pages[i];
						if (page instanceof ManualPageMultiblock)
						{
							ManualPageMultiblock mbPage = (ManualPageMultiblock) page;
							IMultiblock mb = ReflectionHelper.getPrivateValue(ManualPageMultiblock.class, mbPage, 0);
							if (mb != null)
							{
								if (name == null || i == gui.page)
								{
									name = mb.getUniqueName();
								}
							}
						}
					}
				}
			}
			EntityPlayer p = ClientUtils.mc().thePlayer;
			
			ItemStack mainItem = p.getHeldItemMainhand();
			ItemStack offItem = p.getHeldItemOffhand();

			boolean main = mainItem != null && mainItem.getItem() == IEContent.itemTool && mainItem.getItemDamage() == 3;
			boolean off = offItem != null && offItem.getItem() == IEContent.itemTool && offItem.getItemDamage() == 3;
			ItemStack target = main ? mainItem : offItem;
			
			if (main || off)
			{
				IPPacketHandler.INSTANCE.sendToServer(new CloseBookPacket(name));

				if (name == null && ItemNBTHelper.hasKey(target, "lastMultiblock"))
				{
					ItemNBTHelper.remove(target, "lastMultiblock");
				}
				else if (name != null)
				{
					ItemNBTHelper.setString(target, "lastMultiblock", name);
				}
			}
		}

		lastGui = event.getGui();
	}
	
	
	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void renderLast(RenderWorldLastEvent event)
	{
		GlStateManager.pushMatrix();
		Minecraft mc = Minecraft.getMinecraft();
		if (IPConfig.sample_displayBorder && mc.thePlayer != null)
		{
			ItemStack mainItem = mc.thePlayer.getHeldItemMainhand();
			ItemStack secondItem = mc.thePlayer.getHeldItemOffhand();
			
			boolean chunkBorders = false;
			for(EnumHand hand : EnumHand.values())
				if(OreDictionary.itemMatches(new ItemStack(IEContent.blockMetalDevice1,1, BlockTypes_MetalDevice1.SAMPLE_DRILL.getMeta()), ClientUtils.mc().thePlayer.getHeldItem(hand),true))
				{
					chunkBorders = true;
					break;
				}
			if(!chunkBorders && ClientUtils.mc().objectMouseOver!=null && ClientUtils.mc().objectMouseOver.typeOfHit==Type.BLOCK && ClientUtils.mc().theWorld.getTileEntity(ClientUtils.mc().objectMouseOver.getBlockPos()) instanceof TileEntitySampleDrill)
				chunkBorders = true;
			
			boolean main = mainItem != null && mainItem.getItem() instanceof ItemCoresample && ItemNBTHelper.hasKey(mainItem, "coords");
			boolean off = secondItem != null && secondItem.getItem() instanceof ItemCoresample && ItemNBTHelper.hasKey(secondItem, "coords");
			ItemStack target = main ? mainItem : secondItem;
			
			if (!chunkBorders && (main || off))
			{
			
				int[] coords = ItemNBTHelper.getIntArray(target, "coords");
				//World world = DimensionManager.getWorld(coords[0]);
				//if (world.provider.getDimension() == mc.thePlayer.worldObj.provider.getDimension())
				//{
					EntityPlayer player = mc.thePlayer;

					renderChunkBorder(coords[1] << 4, coords[2] << 4);
				//}
			}
		}
		GlStateManager.popMatrix();
	}
	
	@SideOnly(Side.CLIENT)
	public void renderChunkBorder(int chunkX, int chunkZ)
	{
		EntityPlayer player = ClientUtils.mc().thePlayer;

		double px = TileEntityRendererDispatcher.staticPlayerX;
		double py = TileEntityRendererDispatcher.staticPlayerY;
		double pz = TileEntityRendererDispatcher.staticPlayerZ;
		int y = Math.min((int)player.posY-2,player.getEntityWorld().getChunkFromBlockCoords(new BlockPos(chunkX, 0, chunkZ)).getLowestHeight());
		float h = (float)Math.max(32, player.posY-y+4);
		Tessellator tessellator = Tessellator.getInstance();
		VertexBuffer vertexbuffer = tessellator.getBuffer();

		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.disableCull();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		float r = Lib.COLOUR_F_ImmersiveOrange[0];
		float g = Lib.COLOUR_F_ImmersiveOrange[1];
		float b = Lib.COLOUR_F_ImmersiveOrange[2];
		vertexbuffer.setTranslation(chunkX-px, y+2-py, chunkZ-pz);
		GlStateManager.glLineWidth(5f);
		vertexbuffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
		vertexbuffer.pos( 0,0, 0).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos( 0,h, 0).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos(16,0, 0).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos(16,h, 0).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos(16,0,16).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos(16,h,16).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos( 0,0,16).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos( 0,h,16).color(r,g,b,.375f).endVertex();

		vertexbuffer.pos( 0,2, 0).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos(16,2, 0).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos( 0,2, 0).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos( 0,2,16).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos( 0,2,16).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos(16,2,16).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos(16,2, 0).color(r,g,b,.375f).endVertex();
		vertexbuffer.pos(16,2,16).color(r,g,b,.375f).endVertex();
		tessellator.draw();
		vertexbuffer.setTranslation(0, 0, 0);
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.enableCull();
		GlStateManager.disableBlend();
		GlStateManager.enableTexture2D();
	}
	
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void handlePickupItem(RightClickBlock event)
	{
		BlockPos pos = event.getPos();
		IBlockState state = event.getWorld().getBlockState(pos);
		if (!event.getWorld().isRemote && state.getBlock() == IEContent.blockMetalDevice1)
		{
			TileEntity te = event.getWorld().getTileEntity(pos);
			if (te instanceof TileEntitySampleDrill)
			{
				TileEntitySampleDrill drill = (TileEntitySampleDrill) te;
				
				if (drill.dummy != 0)
				{
					te = event.getWorld().getTileEntity(pos.add(0, - drill.dummy, 0));
					if (te instanceof TileEntitySampleDrill)
					{
						drill = (TileEntitySampleDrill) te;
					}
				}
				if (drill.sample != null)
				{
					if(ItemNBTHelper.hasKey(drill.sample, "coords"))
					{
						int[] coords = ItemNBTHelper.getIntArray(drill.sample, "coords");
						World world = DimensionManager.getWorld(coords[0]);
						
						OilWorldInfo info = PumpjackHandler.getOilWorldInfo(world, coords[1], coords[2]);
						if (info.getType() != null)
						{
							ItemNBTHelper.setString(drill.sample, "resType", PumpjackHandler.getOilWorldInfo(world, coords[1], coords[2]).getType().name);
							ItemNBTHelper.setInt(drill.sample, "oil", info.current);
						}
						else
						{
							ItemNBTHelper.setInt(drill.sample, "oil", 0);
						}
					}
				}
			}
		}

	}
	
	@SubscribeEvent(priority = EventPriority.LOWEST)
	@SideOnly(Side.CLIENT)
	public void handleItemTooltip(ItemTooltipEvent event)
	{
		ItemStack stack = event.getItemStack();
		if (stack.getItem() instanceof ItemCoresample)
		{
			if(ItemNBTHelper.hasKey(stack, "oil"))
			{
				String resName = ItemNBTHelper.hasKey(stack, "resType") ? ItemNBTHelper.getString(stack, "resType") : null;
				if (ItemNBTHelper.hasKey(stack, "oil") && resName == null)
				{
					resName = "";
				}
				
				ReservoirType res = null;
				for (ReservoirType type : PumpjackHandler.reservoirList.keySet())
				{
					if (resName.equals(type.name))
					{
						res = type;
					}
				}
				
				int amnt = ItemNBTHelper.getInt(stack, "oil");
				List<String> tooltip = event.getToolTip();
				if (amnt > 0)
				{
					int est = (amnt / 1000) * 1000;
					String test = new DecimalFormat("#,###.##").format(est);
					Fluid f = FluidRegistry.getFluid(res.fluid);
					String fluidName = f.getLocalizedName(new FluidStack(f, 1));
					
					tooltip.add(2, I18n.format("chat.immersivepetroleum.info.coresample.oil", test, fluidName));
				}
				else
				{
					if (res != null && res.replenishRate > 0)
					{
						Fluid f = FluidRegistry.getFluid(res.fluid);
						String fluidName = f.getLocalizedName(new FluidStack(f, 1));
						tooltip.add(2, I18n.format("chat.immersivepetroleum.info.coresample.oilRep", res.replenishRate, fluidName));
					}
					else
					{
						tooltip.add(2, I18n.format("chat.immersivepetroleum.info.coresample.noOil"));
					}
				}
			}
		}
	}
	
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onLogin(PlayerLoggedInEvent event)
	{
		ExcavatorHandler.allowPackets = true;
		if (!event.player.worldObj.isRemote)
		{
			HashMap<ReservoirType, Integer> packetMap = new HashMap<ReservoirType,Integer>();
			for (Entry<ReservoirType,Integer> e: PumpjackHandler.reservoirList.entrySet())
				if (e.getKey() != null && e.getValue() != null)
					packetMap.put(e.getKey(), e.getValue());
			IPPacketHandler.INSTANCE.sendToAll(new MessageReservoirListSync(packetMap));
		}
	}
	
	@SideOnly(Side.CLIENT)
	@SubscribeEvent()
	public void renderCoresampleInfo(RenderGameOverlayEvent.Post event)
	{
		if (ClientUtils.mc().thePlayer!=null && event.getType() == RenderGameOverlayEvent.ElementType.TEXT)
		{
			EntityPlayer player = ClientUtils.mc().thePlayer;

			if (ClientUtils.mc().objectMouseOver!=null)
			{
				boolean hammer = player.getHeldItem(EnumHand.MAIN_HAND) != null && Utils.isHammer(player.getHeldItem(EnumHand.MAIN_HAND));
				RayTraceResult mop = ClientUtils.mc().objectMouseOver;
				if (mop!=null && mop.getBlockPos()!=null)
				{
					TileEntity tileEntity = player.worldObj.getTileEntity(mop.getBlockPos());
					if (tileEntity instanceof TileEntityCoresample)
					{
						IBlockOverlayText overlayBlock = (IBlockOverlayText) tileEntity;
						String[] text = overlayBlock.getOverlayText(ClientUtils.mc().thePlayer, mop, hammer);
						boolean useNixie = overlayBlock.useNixieFont(ClientUtils.mc().thePlayer, mop);
						ItemStack coresample = ((TileEntityCoresample) tileEntity).coresample;
						if (ItemNBTHelper.hasKey(coresample, "oil") && text != null && text.length > 0)
						{
							String resName = ItemNBTHelper.hasKey(coresample, "resType") ? ItemNBTHelper.getString(coresample, "resType") : "";
							int amnt = ItemNBTHelper.getInt(coresample, "oil");
							FontRenderer font = useNixie?ClientProxy.nixieFontOptional:ClientUtils.font();
							int col = (useNixie&& IEConfig.nixietubeFont)?Lib.colour_nixieTubeText:0xffffff;
							int i = text.length;
							
							ReservoirType res = null;
							for (ReservoirType type : PumpjackHandler.reservoirList.keySet())
							{
								if (resName.equals(type.name))
								{
									res = type;
								}
							}
							
							String s = I18n.format("chat.immersivepetroleum.info.coresample.noOil");
							if (amnt > 0)
							{
								int est = (amnt / 1000) * 1000;
								String test = new DecimalFormat("#,###.##").format(est);
								Fluid f = FluidRegistry.getFluid(res.fluid);
								String fluidName = f.getLocalizedName(new FluidStack(f, 1));
								

								s = I18n.format("chat.immersivepetroleum.info.coresample.oil", test, fluidName);
							}
							else if (res != null && res.replenishRate > 0)
							{
								Fluid f = FluidRegistry.getFluid(res.fluid);
								String fluidName = f.getLocalizedName(new FluidStack(f, 1));
								s = I18n.format("chat.immersivepetroleum.info.coresample.oilRep", res.replenishRate, fluidName);
							}
							
							font.drawString(s, event.getResolution().getScaledWidth()/2+8, event.getResolution().getScaledHeight()/2+8+i*font.FONT_HEIGHT, col, true);

	
						}
					}
				}
				else if (mop != null && mop.entityHit != null && mop.entityHit instanceof EntitySpeedboat)
				{
					String[] text = ((EntitySpeedboat) mop.entityHit).getOverlayText(ClientUtils.mc().thePlayer, mop);
					if(text!=null && text.length>0)
					{
						FontRenderer font = ClientUtils.font();
						int col = 0xffffff;
						int i = 0;
						for(String s : text)
							if(s!=null)
								font.drawString(s, event.getResolution().getScaledWidth()/2+8, event.getResolution().getScaledHeight()/2+8+(i++)*font.FONT_HEIGHT, col, true);
					}
				}
			}
		}
	}
	
	@SubscribeEvent
	public void handleBoatImmunity(LivingAttackEvent event)
	{
		if (event.getSource() == DamageSource.lava || event.getSource() == DamageSource.onFire || event.getSource() == DamageSource.inFire)
		{
			EntityLivingBase entity = event.getEntityLiving();
			if (entity.getRidingEntity() instanceof EntitySpeedboat)
			{
				EntitySpeedboat boat = (EntitySpeedboat) entity.getRidingEntity();
				if (boat.isFireproof)
				{
					event.setCanceled(true);
				}
			}
		}
	}
	
	@SubscribeEvent
	public void handleBoatImmunity(PlayerTickEvent event)
	{
		EntityPlayer entity = event.player;
		if (entity.isBurning() && entity.getRidingEntity() instanceof EntitySpeedboat)
		{
			EntitySpeedboat boat = (EntitySpeedboat) entity.getRidingEntity();
			if (boat.isFireproof)
			{
				entity.extinguish();
				DataParameter<Byte> FLAGS = EntitySpeedboat.getFlags();
				byte b0 = ((Byte) entity.getDataManager().get(FLAGS)).byteValue();
					
				entity.getDataManager().set(FLAGS, Byte.valueOf((byte)(b0 & ~(1 << 0))));		
			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void handleBoatImmunity(RenderBlockOverlayEvent event)
	{
		EntityPlayer entity = event.getPlayer();
		if (event.getOverlayType() == OverlayType.FIRE && entity.isBurning() && entity.getRidingEntity() instanceof EntitySpeedboat)
		{
			EntitySpeedboat boat = (EntitySpeedboat) entity.getRidingEntity();
			if (boat.isFireproof)
			{
				event.setCanceled(true);
			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void handleFireRender(RenderPlayerEvent.Pre event)
	{
		EntityPlayer entity = event.getEntityPlayer();
		if (entity.isBurning() && entity.getRidingEntity() instanceof EntitySpeedboat)
		{
			EntitySpeedboat boat = (EntitySpeedboat) entity.getRidingEntity();
			if (boat.isFireproof)
			{
				entity.extinguish();

			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void handleLubricatingMachinesClient(ClientTickEvent event)
	{
		if (event.phase == Phase.END && Minecraft.getMinecraft().theWorld != null)
		{
			handleLubricatingMachines(Minecraft.getMinecraft().theWorld);
		}
	}
	
	@SubscribeEvent
	public void handleLubricatingMachinesServer(WorldTickEvent event)
	{

		if (event.phase == Phase.END)
		{
			handleLubricatingMachines(event.world);
		}
	}
		
	public void handleLubricatingMachines(World world)
	{
		Set<LubricatedTileInfo> toRemove = new HashSet<LubricatedTileInfo>();
		for (LubricatedTileInfo info : LubricatedHandler.lubricatedTiles)
		{
			if (info.world == world.provider.getDimension() && world.isAreaLoaded(info.pos, 0))
			{
				TileEntity te = world.getTileEntity(info.pos);
				ILubricationHandler h = LubricatedHandler.getHandlerForTile(te);
				if (h != null)
				{
					if (h.isMachineEnabled(world, te))
					{
						h.lubricate(world, info.ticks, te);
					}
					if (world.isRemote)
					{
						int n = Block.getStateId(IPContent.blockFluidLubricant.getDefaultState());
						if (te instanceof TileEntityMultiblockPart)
						{
							TileEntityMultiblockPart part = (TileEntityMultiblockPart) te;
							int numBlocks = h.getStructureDimensions()[0] * h.getStructureDimensions()[1] * h.getStructureDimensions()[2];
							for (int i = 0; i < numBlocks; i++)
							{
								BlockPos pos = part.getBlockPosForPos(i);
								TileEntity te2 = world.getTileEntity(info.pos);
								if (te2 != null && te2 instanceof TileEntityMultiblockPart)
								{
									if (((TileEntityMultiblockPart) te2).master() == part.master())
									{
										for (EnumFacing facing : EnumFacing.HORIZONTALS)
										{
											if (world.rand.nextInt(30) == 0 && world.getBlockState(pos.offset(facing)).getBlock().isReplaceable(world, pos.offset(facing)))
											{
												Vec3i direction = facing.getDirectionVec();
												world.spawnParticle(EnumParticleTypes.BLOCK_DUST, 
														pos.getX() + .5f + direction.getX() * .65f, 
														pos.getY() + 1, 
														pos.getZ() + .5f + direction.getZ() * .65f, 
														0, 0, 0, new int[] {n});
											}
										}
									}
								}
							}
						}
					}
					info.ticks--;
					if (info.ticks == 0)
					{
						toRemove.add(info);
					}
				}
			}
		}
		for (LubricatedTileInfo info : toRemove)
		{
			LubricatedHandler.lubricatedTiles.remove(info);
		}
	}

}
