package de.dytanic.cloudnet.ext.bridge.gomint;

import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.ext.bridge.BridgeHelper;
import de.dytanic.cloudnet.ext.bridge.BridgePlayerManager;
import de.dytanic.cloudnet.ext.bridge.gomint.listener.GoMintCloudNetListener;
import de.dytanic.cloudnet.ext.bridge.gomint.listener.GoMintPlayerListener;
import de.dytanic.cloudnet.ext.bridge.listener.BridgeCustomChannelMessageListener;
import de.dytanic.cloudnet.ext.bridge.player.IPlayerManager;
import de.dytanic.cloudnet.ext.bridge.server.BridgeServerHelper;
import de.dytanic.cloudnet.wrapper.Wrapper;
import io.gomint.GoMint;
import io.gomint.event.network.PingEvent;
import io.gomint.plugin.Plugin;
import io.gomint.plugin.PluginName;
import io.gomint.plugin.Version;

import java.util.concurrent.TimeUnit;

@PluginName("CloudNet-Bridge")
@Version(major = 1, minor = 2)
public final class GoMintCloudNetBridgePlugin extends Plugin {

    @Override
    public void onInstall() {
        GoMintCloudNetHelper.init();

        CloudNetDriver.getInstance().getServicesRegistry().registerService(IPlayerManager.class, "BridgePlayerManager", new BridgePlayerManager());

        this.initListeners();

        Wrapper.getInstance().getTaskScheduler().schedule(BridgeHelper::updateServiceInfo);

        this.runFirePingEvent();
    }

    @Override
    public void onUninstall() {
        CloudNetDriver.getInstance().getEventManager().unregisterListeners(this.getClass().getClassLoader());
        Wrapper.getInstance().unregisterPacketListenersByClassLoader(this.getClass().getClassLoader());
    }

    private void initListeners() {
        // GoMint API
        super.registerListener(new GoMintPlayerListener(this));

        // CloudNet
        CloudNetDriver.getInstance().getEventManager().registerListener(new GoMintCloudNetListener(this));
        CloudNetDriver.getInstance().getEventManager().registerListener(new BridgeCustomChannelMessageListener());
    }

    private void runFirePingEvent() {
        super.getScheduler().schedule(() -> {
            PingEvent pingEvent = new PingEvent(
                    GoMintCloudNetHelper.getMotd(),
                    GoMint.instance().getPlayers().size(),
                    GoMintCloudNetHelper.getMaxPlayers()
            );

            boolean hasToUpdate = false;
            boolean value = false;

            GoMint.instance().getPluginManager().callEvent(pingEvent);
            if (pingEvent.getMotd() != null && !pingEvent.getMotd().equalsIgnoreCase(GoMintCloudNetHelper.getApiMotd())) {
                hasToUpdate = true;
                GoMintCloudNetHelper.setMotd(pingEvent.getMotd());
                if (pingEvent.getMotd().toLowerCase().contains("running") ||
                        pingEvent.getMotd().toLowerCase().contains("ingame") ||
                        pingEvent.getMotd().toLowerCase().contains("playing")) {
                    value = true;
                }
            }

            if (pingEvent.getMaxPlayers() != GoMintCloudNetHelper.getMaxPlayers()) {
                hasToUpdate = true;
                GoMintCloudNetHelper.setMaxPlayers(pingEvent.getMaxPlayers());
            }

            if (value) {
                BridgeServerHelper.changeToIngame(true);
                return;
            }

            if (hasToUpdate) {
                BridgeHelper.updateServiceInfo();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }
}