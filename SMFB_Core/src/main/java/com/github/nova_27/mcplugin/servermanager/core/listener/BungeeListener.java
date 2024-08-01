package com.github.nova_27.mcplugin.servermanager.core.listener;

import com.github.nova_27.mcplugin.servermanager.core.Smfb_core;
import com.github.nova_27.mcplugin.servermanager.core.config.ConfigData;
import com.github.nova_27.mcplugin.servermanager.core.config.Server;
import com.github.nova_27.mcplugin.servermanager.core.events.TimerEvent;
import com.github.nova_27.mcplugin.servermanager.core.utils.Messages;
import com.github.nova_27.mcplugin.servermanager.core.utils.Tools;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.OptionalLong;

import static com.github.nova_27.mcplugin.servermanager.core.config.ConfigData.Lobby;
import static com.github.nova_27.mcplugin.servermanager.core.events.TimerEvent.EventType.TimerStopped;

/**
 * BungeeCordイベントリスナー
 */
public class BungeeListener implements Listener {
    /**
     * ログインされたら
     * @param e ログイン情報
     */
    @EventHandler
    public void postLoginEvent(PostLoginEvent e) {
        //一人目の場合
        if(ProxyServer.getInstance().getPlayers().size() == 1) {
            //サーバーを起動
            if (!Lobby.Started) {
                //起動していなかったら、キック

                OptionalLong processTime = Smfb_core.getInstance().getStatistics().getLobbyLastStartProcessTime();
                if (processTime.isPresent()) {
                    long remaining = (long) (Math.floor((double) processTime.getAsLong() / 1000 / 10) * 10);
                    e.getPlayer().disconnect(new TextComponent(Tools.Formatter(Messages.LobbyNotStartedRemaining.toString(), String.valueOf(remaining))));
                } else {
                    e.getPlayer().disconnect(new TextComponent(Messages.LobbyNotStarted.toString()));
                }
                Lobby.StartServer();
            }
            else if (Lobby.Switching){
                //処理中だったら、キック
                OptionalLong processTime = Smfb_core.getInstance().getStatistics().getLobbyLastStartProcessTime();
                if (processTime.isPresent() && Lobby.getLaunchedTime() < 0) {
                    long remaining = processTime.getAsLong() - (System.currentTimeMillis() + Lobby.getLaunchedTime());
                    remaining = (long) (Math.floor((double) remaining / 1000 / 10) * 10);

                    e.getPlayer().disconnect(new TextComponent((0 < remaining) ?
                            Tools.Formatter(Messages.LobbySwitchingRemaining.toString(), String.valueOf(remaining))
                            : Messages.LobbySwitching.toString()));
                } else {
                    e.getPlayer().disconnect(new TextComponent(Messages.LobbySwitching.toString()));
                }

            } else if (Lobby.StopTimer()) {  //起動済みだったらタイマーストップ
                Smfb_core.getInstance().log(Tools.Formatter(Messages.TimerStopped_log.toString(), Lobby.Name));
                Smfb_core.getInstance().getProxy().getPluginManager().callEvent(new TimerEvent(Lobby, TimerStopped));
            }
        }
    }

    /**
     * ログアウトされたら
     * @param e ログアウト情報
     */
    @EventHandler
    public void onLogout(PlayerDisconnectEvent e) {
        if (ProxyServer.getInstance().getPlayers().size() == 1) {
            //0人になったら
            if (Lobby.StartTimer()) {
                Smfb_core.getInstance().log(Tools.Formatter(Messages.TimerStarted_log.toString(), "" + Lobby.CloseTime, Lobby.Name));
                Smfb_core.getInstance().getProxy().getPluginManager().callEvent(new TimerEvent(Lobby, TimerEvent.EventType.TimerStarted));
            }
        }
    }

    /**
     * サーバー間を移動できたら
     */
    @EventHandler
    public void onSwitch(ServerSwitchEvent e) {
        for (Server server : ConfigData.Servers) {
            //ロビーサーバーは除外
            if(server == Lobby) continue;

            //タイマーのストップ
            if(server.equals(e.getPlayer().getServer().getInfo().getName()) && server.StopTimer()) {
                Smfb_core.getInstance().log(Tools.Formatter(Messages.TimerStopped_log.toString(), server.Name));
                ProxyServer.getInstance().broadcast(new TextComponent(Tools.Formatter(Messages.TimerStopped_Minecraft.toString(), server.Name)));
            }
        }
    }

    @EventHandler
    public void onConnect(ServerConnectEvent e) {
        for(Server server : ConfigData.Servers) {
            server.AliveCheck();
        }
    }
}
