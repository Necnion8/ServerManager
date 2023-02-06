package com.github.nova_27.mcplugin.servermanager.core.config;

import com.github.nova_27.mcplugin.servermanager.common.socket.protocol.PacketID;
import com.github.nova_27.mcplugin.servermanager.core.Smfb_core;
import com.github.nova_27.mcplugin.servermanager.core.events.ServerEvent;
import com.github.nova_27.mcplugin.servermanager.core.events.ServerPreStartEvent;
import com.github.nova_27.mcplugin.servermanager.core.events.TimerEvent;
import com.github.nova_27.mcplugin.servermanager.core.socket.ClientConnection;
import com.github.nova_27.mcplugin.servermanager.core.utils.Messages;
import com.github.nova_27.mcplugin.servermanager.core.utils.Requester;
import com.github.nova_27.mcplugin.servermanager.core.utils.Tools;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.config.Configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.github.nova_27.mcplugin.servermanager.core.events.TimerEvent.EventType.TimerRestarted;

public class Server {
    //Minecraftサーバー設定
    public String ID;
    public String Name;
    public int Port;
    private String Dir;
    private String File;
    private String Args;
    private String JavaCmd;
    public int CloseTime = ConfigData.CloseTime;
    public boolean EnableShellCommandMode;
    public String ShellCommand;
    public boolean DisableRemoveControlCodeInShellCommandStdout;
    private boolean calledShellCommand;
    private static final Pattern ControlCodeRegex = Pattern.compile("\u001B\\[[\\d;]*[^\\d;]");
    private static final Pattern ConsoleLinePrefixRegex = Pattern.compile("^>\\.+$");

    //サーバープロセス
    public Process Process = null;
    public boolean Started = false;
    public boolean Switching = false;
    public boolean Enabled = true;

    //ログ
    private static final int BUF_CNT = 30;
    private int Start_write = 0;
    private String[] logs = new String[BUF_CNT];

    //タイマー
    private TimerTask task = null;
    private Timer timer = null;

    /**
     * コンストラクタ
     * @param ID サーバーID（BungeeCordと同一）
     * @param Name サーバー名（表示用）
     * @param Port サーバーポート番号
     * @param Dir サーバーのルートディレクトリ
     * @param File サーバー本体（jar）
     * @param Args 実行引数
     */
    public Server(String ID, String Name, int Port, String Dir, String File, String Args) {
        this.ID = ID;
        this.Name = Name;
        this.Port = Port;
        this.Dir = Dir;
        this.File = File;
        this.Args = Args;
    }

    /**
     * コンストラクタ
     * @param ID サーバーID（BungeeCordと同一）
     * @param Name サーバー名（表示用）
     * @param Port サーバーポート番号
     * @param Dir サーバーのルートディレクトリ
     * @param File サーバー本体（jar）
     * @param Args 実行引数
     * @param JavaCmd Javaコマンド
     */
    public Server(String ID, String Name, int Port, String Dir, String File, String Args, String JavaCmd) {
        this.ID = ID;
        this.Name = Name;
        this.Port = Port;
        this.Dir = Dir;
        this.File = File;
        this.Args = Args;
        this.JavaCmd = JavaCmd;
    }

    private Server getServer() {
        return this;
    }

    /**
     * サーバーをスタートする
     */
    public void StartServer() {
        StartServer(null);
    }

    /**
     * サーバーをスタートする
     * @param requester 起動リクエストの要求オブジェクト
     * @return サーバーの起動を開始したら真を返す
     */
    public boolean StartServer(Requester requester) {
        calledShellCommand = false;

        //有効かつ未処理で開始されていないときは開始
        if(!Started && !Switching && Enabled) {
            if (ProxyServer.getInstance().getPluginManager().callEvent(new ServerPreStartEvent(this, requester)).isCancelled())
                return false;  // cancelled

            try {
                Started = true;
                Switching = true;

                if (EnableShellCommandMode && !ShellCommand.isEmpty()) {
                    // shell mode
                    Process = new ProcessBuilder(Tools.parseCommandArguments(ShellCommand))
                            .directory(new File(Dir))
                            .start();
                    calledShellCommand = true;

                } else {
                    String JavaCmd = (this.JavaCmd != null && !this.JavaCmd.isEmpty()) ? this.JavaCmd : "java";
                    String OS_NAME = System.getProperty("os.name").toLowerCase();
                    if (OS_NAME.startsWith("linux")) {
                        //Linuxの場合
                        Process = new ProcessBuilder("/bin/bash", "-c", "cd  " + Dir + " ; " + JavaCmd + " -jar " + Args + " " + File).start();
                    } else if (OS_NAME.startsWith("windows")) {
                        //Windowsの場合
                        Runtime r = Runtime.getRuntime();
                        Process = r.exec("cmd /c cd " + Dir + " && " + JavaCmd + " -jar " + Args + " " + File);
                    }
                }

                //バッファを読みだしてブロックを防ぐ
                Smfb_core.getInstance().getProxy().getScheduler().schedule(Smfb_core.getInstance(), ()->{
                    try {
                        BufferedReader br = new BufferedReader(new InputStreamReader(Process.getInputStream()));

                        while (true) {
                            if (br.ready()) {
                                //ログを取得
                                String line = br.readLine();

                                if (line == null)
                                    break;

                                if (calledShellCommand && !DisableRemoveControlCodeInShellCommandStdout) {
                                    line = ControlCodeRegex.matcher(line).replaceAll("");
                                    line = ConsoleLinePrefixRegex.matcher(line).replaceAll("");
                                }

                                if (line.isEmpty())
                                    continue;

                                if (Start_write >= BUF_CNT) {
                                    Start_write = 0;
                                }

                                //配列に書き込む
                                logs[Start_write] = line;
                                Start_write++;

                                Smfb_core.getInstance().getProxy().getPluginManager().callEvent(new ServerEvent(this, ServerEvent.EventType.ServerLogged));
                            }else {
                                Thread.sleep(100);
                            }
                        }
                    } catch (IOException | InterruptedException ignored) { }

                    calledShellCommand = false;

                }, 0L, TimeUnit.SECONDS);

                Smfb_core.getInstance().log(Tools.Formatter(Messages.ServerStarting_log.toString(), Name));
                ProxyServer.getInstance().broadcast(new TextComponent(Tools.Formatter(Messages.ServerStarting_minecraft.toString(), Name)));
                Smfb_core.getInstance().getProxy().getPluginManager().callEvent(new ServerEvent(this, ServerEvent.EventType.ServerStarting));
                return true;

            } catch (IOException e) {
                Smfb_core.getInstance().log(Messages.IOError.toString());
            }
        }

        return false;
    }

    public void StopServer() {
        for(ClientConnection cc : Smfb_core.getInstance().getSocketServer().getClientConnections()) {
            if(Objects.equals(cc.getSrcServer(), this)) cc.addQueue(PacketID.ServerStopRequest, new byte[1]);
        }
    }

    /**
     * タイマーの起動
     * @return true タイマーが起動していなかった
     */
    public boolean StartTimer() {
        if (CloseTime <= -1)
            return false;

        if (task == null) {
            task = new TimerTask() {
                @Override
                public void run() {
                    if(!Switching) {
                        //処理中でなかったら
                        Smfb_core.getInstance().log(Tools.Formatter(Messages.ServerStopping_log.toString(), Name));
                        ProxyServer.getInstance().broadcast(new TextComponent(Tools.Formatter(Messages.ServerStopping_Minecraft.toString(), Name)));
                        Smfb_core.getInstance().getProxy().getPluginManager().callEvent(new ServerEvent(getServer(), ServerEvent.EventType.ServerStopping));

                        StopServer();
                    }else{
                        //処理中だったら見送り
                        Smfb_core.getInstance().log(Tools.Formatter(Messages.TimerRestarted_log.toString(), Name));
                        ProxyServer.getInstance().broadcast(new TextComponent(Tools.Formatter(Messages.TimerRestarted_Minecraft.toString(), Name)));
                        Smfb_core.getInstance().getProxy().getPluginManager().callEvent(new TimerEvent(getServer(), TimerRestarted));

                        TimerTask task = this;
                        timer = new Timer();
                        timer.schedule(task, CloseTime * 60000L);
                    }
                }
            };

            timer = new Timer();
            timer.schedule(task, CloseTime * 60000L);

            return true;
        }else{
            return false;
        }
    }

    /**
     * タイマーのストップ
     */
    public boolean StopTimer() {
        if(task == null) return false;

        boolean cancelled = task.cancel();
        task = null;
        return cancelled;
    }

    /**
     * サーバーが動いているかチェックする
     */
    public void AliveCheck() {
        if((Started || Switching) && !Process.isAlive()) {
            ProxyServer.getInstance().broadcast(new TextComponent(Tools.Formatter(Messages.ProcessDied.toString(), Name)));
            Smfb_core.getInstance().getProxy().getPluginManager().callEvent(new ServerEvent(this, ServerEvent.EventType.ServerErrorHappened));
            Switching = false;
            Started = false;
            Process.destroy();
        }
    }

    /**
     * サーバーIDからサーバーを取得する
     * @param ID サーバーID
     * @return サーバー
     */
    public static Server getServerByID(String ID) {
        for(Server server : ConfigData.Servers) {
            if(server.ID.equals(ID)) {
                return server;
            }
        }

        return null;
    }

    /**
     * サーバーのステータスを文字で返す
     * @return ステータス
     */
    public String Status() {
        if(!Enabled) return Messages.ServerStatus_disabled.toString();

        if(Started) {
            if(!Switching) {
                return Messages.ServerStatus_started.toString();
            }else{
                return Messages.ServerStatus_starting.toString();
            }
        }else{
            if(!Switching) {
                return Messages.ServerStatus_stopped.toString();
            }else{
                return Messages.ServerStatus_stopping.toString();
            }
        }
    }

    /**
     * 最新ログを読む
     * @param num 読むログの行数
     * @return ログ
     */
    public String getLatestLog(int num) {
        int reading = Start_write - 1;
        String readLogs = "";

        for(int i = 0; i <= num; i++) {
            if(logs[reading] == null) break;
            readLogs += logs[reading] + "\n";

            reading--;
            if (reading < 0) {
                reading = BUF_CNT - 1;
            }
        }

        return readLogs;
    }

    /**
     * サーバーの設定を読み込む
     * @param section サーバー設定を含んだConfiguration
     */
    public void LoadConfig(Configuration section) {
        Name = section.getString("Name");
        Dir = section.getString("Dir");
        File = section.getString("File");
        Args = section.getString("Args");
        JavaCmd = section.getString("JavaCmd");
        CloseTime = section.contains("CloseTime") ? section.getInt("CloseTime") : ConfigData.CloseTime;

        EnableShellCommandMode = section.getBoolean("EnableShellCommandMode");
        ShellCommand = section.getString("ShellCommand");
        DisableRemoveControlCodeInShellCommandStdout = section.getBoolean("DisableRemoveControlCodeInStdout", false);
    }

    /**
     * シェルコマンドモードで起動しているなら真を返す
     */
    public boolean RunningByShellCommand() {
        return calledShellCommand;
    }

}
