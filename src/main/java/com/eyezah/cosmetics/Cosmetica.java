package com.eyezah.cosmetics;

import cc.cosmetica.api.Cape;
import cc.cosmetica.api.CosmeticaAPI;
import cc.cosmetica.api.CustomCape;
import cc.cosmetica.api.Model;
import cc.cosmetica.api.ShoulderBuddies;
import cc.cosmetica.api.User;
import com.eyezah.cosmetics.cosmetics.Hats;
import com.eyezah.cosmetics.cosmetics.PlayerData;
import com.eyezah.cosmetics.cosmetics.model.BakableModel;
import com.eyezah.cosmetics.cosmetics.model.Models;
import com.eyezah.cosmetics.screens.LoadingScreen;
import com.eyezah.cosmetics.utils.Debug;
import com.eyezah.cosmetics.utils.NamedThreadFactory;
import com.eyezah.cosmetics.utils.SpecialKeyMapping;
import com.eyezah.cosmetics.utils.TextComponents;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.eyezah.cosmetics.Authentication.runAuthenticationCheckThread;

@Environment(EnvType.CLIENT)
public class Cosmetica implements ClientModInitializer {
	public static String authServer;
	public static String websiteHost;
	public static CosmeticaAPI api;

	// for cosmetic sniper
	public static Player farPickPlayer;
	public static HitResult farPickHitResult;

	// for welcome & vcheck
	public static Component displayNext;

	public static String currentServerAddressCache = "";
	public static KeyMapping openCustomiseScreen;
	public static KeyMapping snipe;

	private static Map<UUID, PlayerData> playerDataCache = new HashMap<>();
	private static Map<UUID, List<Consumer<PlayerData>>> synchronisedRequestsThatGotTheTempValueAndAreWaitingForTheRealData = new HashMap<>();
	private static Set<UUID> lookingUp = new HashSet<>();

	public static final Logger LOGGER = LogManager.getLogger("Cosmetica");

	private static final ExecutorService MAIN_POOL = Executors.newFixedThreadPool(
			Integer.parseInt(System.getProperty("cosmetica.lookupThreads", "8")),
			new NamedThreadFactory("Cosmetica Lookup Thread"));

	private static CosmeticaConfig config;
	private static DefaultSettingsConfig defaultSettingsConfig;
	private static Path configDirectory;
	private static Path cacheDirectory;

	/**
	 * The timestamp for the africa endpoint.
	 */
	private static OptionalLong toto = OptionalLong.empty();
	private static final Pattern UNDASHED_UUID_GAPS = Pattern.compile("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})");
	private static final String UUID_DASHIFIER_REPLACEMENT = "$1-$2-$3-$4-$5";

	private static final List<String> splashes = new LinkedList<>();

	private static void addSplash(String splash) {
		splashes.add(splash);
	}

	public static Collection<String> getSplashes() {
		return splashes;
	}

	public static CosmeticaConfig getConfig() {
		return config;
	}

	public static Path getConfigDirectory() {
		return configDirectory;
	}

	public static Path getCacheDirectory() {
		return cacheDirectory;
	}

	public static DefaultSettingsConfig getDefaultSettingsConfig() {
		return defaultSettingsConfig;
	}

	@Override
	public void onInitializeClient() {
		config = new CosmeticaConfig(FabricLoader.getInstance().getConfigDir().resolve("cosmetica").resolve("cosmetica.properties"));
		configDirectory = FabricLoader.getInstance().getConfigDir().resolve("cosmetica");
		defaultSettingsConfig = new DefaultSettingsConfig(FabricLoader.getInstance().getConfigDir().resolve("cosmetica").resolve("default-settings.properties"));

		Path minecraftDir = findDefaultInstallDir("minecraft");

		if (Files.isDirectory(minecraftDir)) {
			cacheDirectory = minecraftDir.resolve(".cosmetica");
		}
		else {
			cacheDirectory = FabricLoader.getInstance().getGameDir().resolve(".cosmetica");
		}

		// create cache directory if it doesn't exist
		if (!Files.exists(cacheDirectory)) {
			try {
				Files.createDirectory(cacheDirectory);

				// stupid windows
				if (Util.getPlatform() == Util.OS.WINDOWS) {
					try {
						Files.setAttribute(cacheDirectory, "dos:hidden", true);
					} catch (Exception e) {
						Cosmetica.LOGGER.warn("Failed to set dos:hidden for cache file on windows", e);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException("Error creating cache directory", e);
			}
		}

		try {
			config.initialize();
			defaultSettingsConfig.initialize();
		} catch (IOException e) {
			LOGGER.warn("Failed to load config, falling back to defaults!");
			e.printStackTrace();
		}

		// delete debug dump images
		Debug.clearImages();
		
		// API Url Getter
		runOffthread(() -> {
			File apiCache = new File(cacheDirectory.toFile(), "cosmetica_get_api_cache.json");
			//System.out.println(apiCache.getAbsolutePath());

			CosmeticaAPI.setAPICache(apiCache);

			try {
				api = CosmeticaAPI.newUnauthenticatedInstance();
				api.setUrlLogger(str -> Debug.checkedInfo(str, "always_print_urls"));

				Debug.info("Finished retrieving API Url. Conclusion: the API should be contacted at " + CosmeticaAPI.getAPIServer());
				LOGGER.info(CosmeticaAPI.getMessage());
				splashes.add(CosmeticaAPI.getMessage());

				Cosmetica.authServer = CosmeticaAPI.getAuthServer();
				Cosmetica.websiteHost = CosmeticaAPI.getWebsite();
				Authentication.runAuthentication(1);

				api.checkVersion(
						SharedConstants.getCurrentVersion().getId(),
						FabricLoader.getInstance().getModContainer("cosmetica").get().getMetadata().getVersion().getFriendlyString()
				).ifSuccessfulOrElse(versionInfo -> {
					String s = versionInfo.minecraftMessage();

					if (!s.isEmpty()) {
						displayNext = TextComponents.literal(s);
					}
				}, Cosmetica.logErr("Error checking version"));
			} catch (Exception e) {
				LOGGER.error("Error retrieving API Url. Mod functionality will be disabled!");
				e.printStackTrace();
			}
		}, ThreadPool.GENERAL_THREADS);

		ClientSpriteRegistryCallback.event(InventoryMenu.BLOCK_ATLAS).register((atlasTexture, registry) -> {
			// register all reserved textures
			for (int i = 0; i < 128; ++i) {
				registry.register(new ResourceLocation("cosmetica", "generated/reserved_" + i));
			}
		});

		// make sure it clears relevant caches on resource reload
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return new ResourceLocation("cosmetica", "cache_clearer");
			}

			@Override
			public void onResourceManagerReload(ResourceManager resourceManager) {
				Models.resetTextureBasedCaches(); // reset only the caches that need to be reset after a resource reload
			}
		});

		runAuthenticationCheckThread();
	}

	public static void registerKeyMappings(List<KeyMapping> keymappings) {
		keymappings.add(openCustomiseScreen = new SpecialKeyMapping(
				"key.cosmetica.customise",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(), // not bound by default
				"key.categories.misc"
		));

		keymappings.add(snipe = new SpecialKeyMapping(
				"key.cosmetica.snipe",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(), // not bound by default
				"key.categories.misc"
		));
	}

	public static boolean isProbablyNPC(UUID uuid) {
		return uuid.version() == 2; // NPCs are uuid version 2. Of course, this can't always be guaranteed with the many different server software, but it seems to be the case at least on hypixel
	}

	private static String loadOrCache(File file, @Nullable String value) {
		try {
			if (value != null) {
				file.createNewFile();

				try (FileWriter writer = new FileWriter(file)) {
					writer.write(value);
				}
			} else if (file.isFile()) {
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					value = reader.readLine().trim();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return value;
	}

	/*
	 * Adapted from code at https://github.com/FabricMC/fabric-installer
	 * Original license has been preserved for this method.
	 *
	 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
	 *
	 * Licensed under the Apache License, Version 2.0 (the "License");
	 * you may not use this file except in compliance with the License.
	 * You may obtain a copy of the License at
	 *
	 *     http://www.apache.org/licenses/LICENSE-2.0
	 *
	 * Unless required by applicable law or agreed to in writing, software
	 * distributed under the License is distributed on an "AS IS" BASIS,
	 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	 * See the License for the specific language governing permissions and
	 * limitations under the License.
	 */
	private static Path findDefaultInstallDir(String application) {
		String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
		Path dir;

		if (os.contains("win") && System.getenv("APPDATA") != null) {
			dir = Paths.get(System.getenv("APPDATA")).resolve("." + application);
		} else {
			String home = System.getProperty("user.home", ".");
			Path homeDir = Paths.get(home);

			if (os.contains("mac")) {
				dir = homeDir.resolve("Library").resolve("Application Support").resolve(application);
			} else {
				dir = homeDir.resolve("." + application);
			}
		}

		return dir.toAbsolutePath().normalize();
	}

	public static void onShutdownClient() {
		try {
			MAIN_POOL.shutdownNow();
		} catch (RuntimeException e) { // Just in case.
			e.printStackTrace();
		}
	}

	// example fabristation connection
	public static String getFabriStationActivity() {
		System.out.println("running activity check");
		if (FabricLoader.getInstance().isModLoaded("fabristation")) {
			System.out.println("Station is loaded");
			//return "connected";
			return FabriStationConnector.getFormatted();
		} else {
			System.out.println("Station isn't loaded");
			return "not connected";
		}
	}

	// =================
	//       IMPL
	// =================

	// Start Africa

	public static String dashifyUUID(String uuid) {
		return UNDASHED_UUID_GAPS.matcher(uuid).replaceAll(UUID_DASHIFIER_REPLACEMENT);
	}

	public static String base64Ip(InetSocketAddress ip) {
		byte[] arr = (ip.getAddress().getHostAddress() + ":" + ip.getPort()).getBytes(StandardCharsets.UTF_8);
		return Base64.encodeBase64String(arr);
	}

	public static void safari(Minecraft minecraft, boolean yourFirstRodeo, boolean ignoreSelf) {
		InetSocketAddress prideRock = minecraft.isLocalServer() ? new InetSocketAddress("127.0.0.1", 25565) : null;
		if (prideRock == null && minecraft.getConnection().getConnection().getRemoteAddress() instanceof InetSocketAddress ip)
			prideRock = ip;
		if (prideRock != null)
			safari(prideRock, yourFirstRodeo, ignoreSelf);
	}

	public static void safari(InetSocketAddress prideRock, boolean yourFirstRodeo, boolean ignoreSelf) {
		if (api != null && api.isAuthenticated()) {
			Debug.info("Thread for safari {}", Thread.currentThread().getName());

			api.everyThirtySecondsInAfricaHalfAMinutePasses(prideRock, yourFirstRodeo || !Cosmetica.toto.isPresent() ? 0 : Cosmetica.toto.getAsLong())
					.ifSuccessfulOrElse(theLionSleepsTonight -> {
						// the speech from the lion king
						for (String notification : theLionSleepsTonight.getNotifications()) { // let's hope I made sure this isn't null
							try {
								Minecraft.getInstance().gui.getChat().addMessage(
										new TextComponent("??6??lCosmetica??f ??l>??7 ").append(TextComponents.chatEncode(notification))
								);
							}
							catch (Exception e) {
								Cosmetica.LOGGER.error("Error sending cosmetica notification.", e);
							}
						}

						Cosmetica.toto = OptionalLong.of(theLionSleepsTonight.getTimestamp());

						if (!yourFirstRodeo) {
							Debug.info("Processing updates found on the safari.");

							for (User individual : theLionSleepsTonight.getNeedsUpdating()) {
								UUID uuid = individual.getUUID();
								Debug.info("Your amazing lion king with expected uuid {} seems to be requesting we update his (or her, their, faer, ...) cosmetics! :lion:", uuid);

								if (playerDataCache.containsKey(uuid)) {
									clearPlayerData(uuid);

									// if ourselves, refresh asap
									if (!ignoreSelf && uuid.equals(Minecraft.getInstance().player.getUUID())) {
										getPlayerData(Minecraft.getInstance().player);
									}
								} else {
									// Here are EyezahMC inc. we strive to be extremely descriptive with our debug messages.
									Debug.info("Lol cringe they went scampering into a bush or something!");

									// use username to clear the info - might be in offline mode or something
									String username = individual.getUsername();

									PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(username);

									if (info != null) {
										UUID serverUuid = info.getProfile().getId();

										if (playerDataCache.containsKey(serverUuid)) {
											Debug.info("Found them :). They were hiding at uuid {}", serverUuid);
											clearPlayerData(serverUuid);

											// if ourselves, refresh asap
											if (!ignoreSelf && username.equals(String.valueOf(Minecraft.getInstance().player.getName()))) {
												getPlayerData(Minecraft.getInstance().player);
											}
										}
									}
								}
							}
						}
			}, logErr("Error checking for cosmetic updates on the remote server"));
		}
	}

	// End Africa

	public static void cinder(Minecraft minecraft, float yawProbably) {
		Entity entity = minecraft.getCameraEntity();

		if (entity != null) {
			if (minecraft.level != null) {
				minecraft.getProfiler().push("snipe");
				Cosmetica.farPickPlayer = null;

				final double maxDist = 64.0f;
				Cosmetica.farPickHitResult = entity.pick(maxDist, yawProbably, false);
				Vec3 eyePosition = entity.getEyePosition(yawProbably);

				double maxDistSqr = maxDist;
				maxDistSqr *= maxDistSqr;

				if (Cosmetica.farPickHitResult != null) {
					maxDistSqr = Cosmetica.farPickHitResult.getLocation().distanceToSqr(eyePosition);
				}

				Vec3 view = entity.getViewVector(1.0F);
				Vec3 castTowards = eyePosition.add(view.x * maxDist, view.y * maxDist, view.z * maxDist);

				final float inflation = 1.0F;
				AABB selectionBoundingBox = entity.getBoundingBox().expandTowards(view.scale(maxDist)).inflate(inflation, inflation, inflation);
				EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(entity, eyePosition, castTowards, selectionBoundingBox, e -> !e.isSpectator() && e.isPickable(), maxDistSqr);

				if (entityHitResult != null) {
					Entity entity2 = entityHitResult.getEntity();
					Vec3 resultLocation = entityHitResult.getLocation();
					double distance = eyePosition.distanceToSqr(resultLocation);

					if (distance < maxDistSqr || Cosmetica.farPickHitResult == null) {
						Cosmetica.farPickHitResult = entityHitResult;

						if (entity2 instanceof Player player) { // vanilla crosshair pick: entity2 instanceof LivingEntity || entity2 instanceof ItemFrame
							Cosmetica.farPickPlayer = player;
						}
					}
				}

				minecraft.getProfiler().pop();
			}
		}
	}

	public static boolean handleComponentClicked(Minecraft minecraft, Style style) {
		// handle clicking the "here" in the welcome message
		if (style != null && style.getClickEvent() != null && style.getClickEvent().getAction() == ClickEvent.Action.CHANGE_PAGE && style.getClickEvent().getValue().equals("cosmetica.customise")) {
			minecraft.setScreen(new LoadingScreen(null, Minecraft.getInstance().options, 1));
			return true;
		}

		return false;
	}

	@Nullable
	public static void runOffthread(Runnable runnable, @SuppressWarnings("unused") ThreadPool pool) {
		if (Thread.currentThread().getName().startsWith("Cosmetica")) {
			runnable.run();
		} else {
			MAIN_POOL.execute(runnable);
		}
	}

	public static boolean shouldRenderUpsideDown(Player player) {
		return getPlayerData(player).upsideDown();
	}

	public static PlayerData getPlayerData(Player player) {
		return getPlayerData(player.getUUID(), player.getName().getString(), false);
	}

	public static PlayerData getCachedPlayerData(UUID player) {
		synchronized (playerDataCache) {
			return playerDataCache.get(player);
		}
	}

	public static void clearPlayerData(UUID uuid) {
		synchronized (playerDataCache) {
			playerDataCache.remove(uuid);
		}
	}

	public static int getCacheSize() {
		synchronized (playerDataCache) {
			return playerDataCache.size();
		}
	}

	public static Collection<UUID> getCachedPlayers() {
		synchronized (playerDataCache) {
			return playerDataCache.keySet();
		}
	}

	public static boolean isPlayerCached(UUID uuid) {
		synchronized (playerDataCache) {
			return playerDataCache.containsKey(uuid);
		}
	}

	public static String urlEncode(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException(ex.getCause());
		}
	}

	public static <K, V, V2> Map<K, V2> map(Map<K, V> original, Function<V, V2> mapper) {
		HashMap<K, V2> result = new HashMap<>();
		original.forEach((k, v) -> result.put(k, mapper.apply(v)));
		return result;
	}

	public static String pickFirst(String... strings) {
		for (String s : strings) {
			if (!s.isEmpty()) return s;
		}

		return "";
	}

	// TODO this code is cursed from editing and editing and editing. Despaghettify this.
	// could split into a system of data listeners/dispatchers to try clean up
	public static PlayerData getPlayerData(UUID uuid, String username, boolean sync) {
		if (Cosmetica.isProbablyNPC(uuid)) return PlayerData.NONE;
		Level level = Minecraft.getInstance().level;

		AtomicReference<PlayerData> theDefaultValue = new AtomicReference<>(PlayerData.NONE);
		AtomicReference<Supplier<PlayerData>> lookup = new AtomicReference<>(() -> theDefaultValue.get());

		synchronized (playerDataCache) { // TODO if the network connection fails, queue it to try again later
			theDefaultValue.set(playerDataCache.computeIfAbsent(uuid, uid -> {
				if (!lookingUp.contains(uuid)) { // if not already looking up, mark as looking up and look up.
					lookingUp.add(uuid);

					Supplier<PlayerData> request = () -> {
						if (Cosmetica.api == null || Minecraft.getInstance().level != level) { // don't make the request if the level changed (in case the players are different between levels)!
							synchronized (playerDataCache) { // make sure temp values are removed
								playerDataCache.remove(uuid);
								lookingUp.remove(uuid);
							}

							return PlayerData.NONE;
						}

						AtomicReference<PlayerData> newDataHolder = new AtomicReference<>(PlayerData.NONE);

						Cosmetica.api.getUserInfo(uuid, username).ifSuccessfulOrElse(info -> {
							List<Model> hats = info.getHats();
							Optional<ShoulderBuddies> shoulderBuddies = info.getShoulderBuddies();
							Optional<Model> backBling = info.getBackBling();
							Optional<Cape> cloak = info.getCape();

							Optional<Model> leftShoulderBuddy = shoulderBuddies.isEmpty() ? Optional.empty() : shoulderBuddies.get().getLeft();
							Optional<Model> rightShoulderBuddy = shoulderBuddies.isEmpty() ? Optional.empty() : shoulderBuddies.get().getRight();

							PlayerData newData = new PlayerData(
									info.getLore(),
									info.isUpsideDown(),
									info.getPrefix(),
									info.getSuffix(),
									hats.stream().map(Models::createBakableModel).collect(Collectors.toList()),
									leftShoulderBuddy.isEmpty() ? null : Models.createBakableModel(leftShoulderBuddy.get()),
									rightShoulderBuddy.isEmpty() ? null : Models.createBakableModel(rightShoulderBuddy.get()),
									backBling.isEmpty() ? null : Models.createBakableModel(backBling.get()),
									cloak.isEmpty() ? "" : pickFirst(cloak.get().getName(), cloak.get().getOrigin() + " Cape"),
									cloak.isEmpty() ? "none" : cloak.get().getId(),
									cloak.isPresent() && !cloak.get().isCosmeticaAlternative() && !(cloak.get() instanceof CustomCape),
									cloak.isEmpty() ? null : CosmeticaSkinManager.processCape(cloak.get()),
									CosmeticaSkinManager.processSkin(info.getSkin(), uuid),
									info.isSlim()
							);

							synchronized (playerDataCache) { // update the information with what we have gotten.
								playerDataCache.put(uuid, newData);
								lookingUp.remove(uuid);
							}

							synchronized (synchronisedRequestsThatGotTheTempValueAndAreWaitingForTheRealData) {
								@Nullable var waitingRequests = synchronisedRequestsThatGotTheTempValueAndAreWaitingForTheRealData.remove(uuid);
								if (waitingRequests != null) waitingRequests.forEach(c -> c.accept(newData));
							}

							newDataHolder.set(newData);
						}, logErr("Error getting user info for " + uuid + " / " + username));

						return newDataHolder.get();
					};

					if (sync) lookup.set(request);
					else lookup.set(() -> {
						Cosmetica.runOffthread(() -> request.get(), ThreadPool.GENERAL_THREADS);
						return PlayerData.TEMPORARY;
					});
				}

				return PlayerData.TEMPORARY; // temporary name: blank.
			}));
		}

		// to ensure web requests are not run in a synchronised block on the data cache, holding up the main thread
		// also return the actual data


		PlayerData result = lookup.get().get();

		if (sync && result == PlayerData.TEMPORARY) {
			AtomicReference<PlayerData> resultt = new AtomicReference<>(PlayerData.TEMPORARY);

			synchronized (synchronisedRequestsThatGotTheTempValueAndAreWaitingForTheRealData) {
				synchronized (playerDataCache) {
					result = playerDataCache.get(uuid);
				}

				if (result == null) return PlayerData.NONE; // idk if this could really happen (it would have to be removed in a short span of time) but just in case lmao
				if (result != PlayerData.TEMPORARY) return result;

				Cosmetica.LOGGER.warn("Synchronised player info request is waiting for the request on another thread to respond.");

				// if still pending, wait on the object
				synchronisedRequestsThatGotTheTempValueAndAreWaitingForTheRealData.computeIfAbsent(uuid, l -> new LinkedList<>()).add(resultt::set);
			}

			while (resultt.get() == PlayerData.TEMPORARY) {
				try {
					Thread.sleep(5L);
				}
				catch (InterruptedException e) {
					Cosmetica.LOGGER.warn("Exception while synchronised thread waits for data", e);
				}
			}
		}

		return result;
	}

	public static void renderLore(EntityRenderDispatcher entityRenderDispatcher, Entity entity, PlayerModel<AbstractClientPlayer> playerModel, PoseStack stack, MultiBufferSource multiBufferSource, Font font, int packedLight) {
		if (entity instanceof Player player) {
			UUID lookupId = player.getUUID();

			if (lookupId != null) {
				double squaredDistance = entityRenderDispatcher.distanceToSqr(entity);
				PlayerData data = getPlayerData(player);

				if (squaredDistance <= 4096.0D) {
					renderLore(
							stack,
							entityRenderDispatcher.cameraOrientation(),
							font,
							multiBufferSource,
							data.lore(),
							Hats.OVERRIDDEN.getList(() -> data.hats()),
							player.hasItemInSlot(EquipmentSlot.HEAD),
							entity.isDiscrete(),
							data.upsideDown(),
							entity.getBbHeight(),
							playerModel.head.xRot,
							packedLight);
				}
			}
		}
	}

	public static void renderLore(PoseStack stack, Quaternion cameraOrientation, Font font, MultiBufferSource multiBufferSource, String lore, List<BakableModel> hats, boolean wearingHelmet, boolean sneaking, boolean upsideDown, float playerHeight, float xRotHead, int packedLight) {
		if (!lore.equals("")) {
			// how much do we need to shift up nametags?

			// upside down players don't need nametags shifted up
			if (!upsideDown) {
				float hatTopY = 0;

				for (BakableModel hat : hats) {
					if (!((hat.extraInfo() & 0x1) == 0 && wearingHelmet)) {
						hatTopY = Math.max(hatTopY, (float) hat.bounds().y1());
					}
				}

				if (hatTopY > 0) {
					float normalizedAngleMultiplier = (float) -(Math.abs(xRotHead) / 1.57 - 1);
					float lookAngleMultiplier;
					if (normalizedAngleMultiplier == 0.49974638F) { // Gliding with elytra, swimming, or crouching
						lookAngleMultiplier = 0;
					} else {
						lookAngleMultiplier = normalizedAngleMultiplier;
					}
					stack.translate(0, (hatTopY / 16) * lookAngleMultiplier, 0);
				}
			}

			// render lore

			Component component = new TextComponent(lore);

			boolean bl = !sneaking;

			float height = playerHeight + 0.25F;

			stack.translate(0, 0.1, 0);

			stack.pushPose();
			stack.translate(0.0D, height, 0.0D);
			stack.mulPose(cameraOrientation);
			stack.scale(-0.025F, -0.025F, 0.025F);
			stack.scale(0.75F, 0.75F, 0.75F);
			Matrix4f textModel = stack.last().pose();

			@SuppressWarnings("resource")
			float backgroundOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
			int alphaARGB = (int) (backgroundOpacity * 255.0F) << 24;

			float xOffset = (float) (-font.width(component) / 2);

			font.drawInBatch(component, xOffset, 0, 553648127, false, textModel, multiBufferSource, bl, alphaARGB, packedLight);

			if (bl) {
				font.drawInBatch(component, xOffset, 0, -1, false, textModel, multiBufferSource, false, 0, packedLight);
			}

			stack.popPose();
		}
	}

	private static Vector3f rotateVertex(Vector3f vertex, Vector3f origin, Direction.Axis axis, float angle) {
		vertex.sub(origin);
		if (axis == Direction.Axis.X) {
			return new Vector3f(vertex.x() + origin.x(), (float) (vertex.y() * Math.cos(angle) - vertex.z() * Math.sin(angle)) + origin.y(), (float) (vertex.z() * Math.cos(angle) + vertex.y() * Math.sin(angle)) + origin.z());
		} else if (axis == Direction.Axis.Y) {
			return new Vector3f((float) (vertex.x() * Math.cos(angle) + vertex.z() * Math.sin(angle)) + origin.x(), vertex.y() + origin.y(), (float) (vertex.z() * Math.cos(angle) - vertex.x() * Math.sin(angle)) + origin.z());
		} else if (axis == Direction.Axis.Z) {
			return new Vector3f((float) (vertex.x() * Math.cos(angle) - vertex.y() * Math.sin(angle)) + origin.x(), (float) (vertex.y() * Math.cos(angle) + vertex.x() * Math.sin(angle)) + origin.y(), vertex.z() + origin.z());
		}

		throw new UnsupportedOperationException();
	}

	public static void clearAllCaches() {
		Debug.info("Clearing all Cosmetica Caches");
		playerDataCache = new HashMap<>();
		Models.resetCaches();
		CosmeticaSkinManager.clearCaches();
		System.gc(); // force jvm to garbage collect our unused data
	}

	public static Consumer<RuntimeException> logErr(String message) {
		return e -> LOGGER.error(message + ": {}", e);
	}
}
