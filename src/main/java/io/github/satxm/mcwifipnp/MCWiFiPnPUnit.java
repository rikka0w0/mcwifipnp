package io.github.satxm.mcwifipnp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dosse.upnp.UPnP;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.BanIpCommands;
import net.minecraft.server.commands.BanListCommands;
import net.minecraft.server.commands.BanPlayerCommands;
import net.minecraft.server.commands.DeOpCommands;
import net.minecraft.server.commands.OpCommand;
import net.minecraft.server.commands.PardonCommand;
import net.minecraft.server.commands.PardonIpCommand;
import net.minecraft.server.commands.PublishCommand;
import net.minecraft.server.commands.WhitelistCommand;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;

public class MCWiFiPnPUnit {
  public static final String MODID = "mcwifipnp";
  public static final Component MODIFY_LAN_OPTIONS = Component.translatable("mcwifipnp.gui.lanServerOptions");

  public static final Logger LOGGER = LogManager.getLogger(MCWiFiPnP.class);

  /**
   * Register commands.
   * Should be called from platform-specific entries.
   */
  public static void registerCommands(CommandDispatcher<CommandSourceStack> cmdDispatcher) {
    DeOpCommands.register(cmdDispatcher);
    OpCommand.register(cmdDispatcher);
    WhitelistCommand.register(cmdDispatcher);
    BanIpCommands.register(cmdDispatcher);
    BanListCommands.register(cmdDispatcher);
    BanPlayerCommands.register(cmdDispatcher);
    PardonCommand.register(cmdDispatcher);
    PardonIpCommand.register(cmdDispatcher);
    ForceOfflineCommand.register(cmdDispatcher);
  }

  public static void publishServer(Config cfg) {
    Minecraft client = Minecraft.getInstance();
    IntegratedServer server = client.getSingleplayerServer();
    PlayerList playerList = server.getPlayerList();

    MutableComponent component = server.publishServer(cfg.gameType, cfg.allowHostCheat, cfg.port)
        ? PublishCommand.getSuccessMessage(cfg.port)
        : Component.translatable("commands.publish.failed");
    client.gui.getChat().addMessage(component);
    playerList.getOps().add(new ServerOpListEntry(server.getSingleplayerProfile(), 4, playerList.canBypassPlayerLimit(server.getSingleplayerProfile())));

    UPnPModule.startIfEnabled(server, cfg);
    new Thread(() -> {
      MCWiFiPnPUnit.CopyToClipboard(cfg, client);
    }, "MCWiFiPnP").start();
  }

  public static void CopyToClipboard(Config cfg, Minecraft client) {
    if (cfg.getPublicIP) {
      ArrayList<Component> IPComponentList = new ArrayList<Component>();
      ArrayList<String> IPList = new ArrayList<String>();
      for (int i = 0; i < IPAddressList().size(); i++) {
        Map<String, String> NewMap = IPAddressList().get(i);
        if (NewMap.get("Type") == "IPv4") {
          IPComponentList.add(IPComponent(
              Component.translatable(NewMap.get("Local")).getString() + " " + NewMap.get("Type"),
              NewMap.get("IP") + ":" + cfg.port));
        } else {
          IPComponentList.add(IPComponent(
              Component.translatable(NewMap.get("Local")).getString() + " " + NewMap.get("Type"),
              "[" + NewMap.get("IP") + "]:" + cfg.port));
        }
        IPList.add(NewMap.get("IP"));
      }
      if (!GetGlobalIPv4().isEmpty() && !IPList.contains(GetGlobalIPv4().get("IP"))) {
        IPComponentList.add(IPComponent(
            Component.translatable(GetGlobalIPv4().get("Local")).getString() + " "
                + GetGlobalIPv4().get("Type"),
            GetGlobalIPv4().get("IP") + ":" + cfg.port));
        IPList.add(GetGlobalIPv4().get("IP"));
      }
      if (!GetGlobalIPv6().isEmpty() && !IPList.contains(GetGlobalIPv6().get("IP"))) {
        IPComponentList.add(IPComponent(Component.translatable(GetGlobalIPv6().get("Local")).getString() + " "
            + GetGlobalIPv6().get("Type"), "[" + GetGlobalIPv6().get("IP") + "]:" + cfg.port));
        IPList.add(GetGlobalIPv4().get("IP"));
      }
      if (cfg.useUPnP && UPnP.getExternalIP() != null && !IPList.contains(GetGlobalIPv6().get("IP"))) {
        IPComponentList.add(IPComponent("UPnP IPv4", UPnP.getExternalIP() + ":" + cfg.port));
        IPList.add(UPnP.getExternalIP());
      }
      if (IPList.isEmpty()) {
        client.gui.getChat().addMessage(Component.translatable("mcwifipnp.upnp.cantgetip"));
      } else {
        MutableComponent component = null;
        for (int i = 0; i < IPComponentList.size(); i++) {
          if (component == null) {
            component = IPComponentList.get(i).copy();
          } else {
            component.append(IPComponentList.get(i));
          }
        }
        client.gui.getChat().addMessage(
            Component.translatable("mcwifipnp.upnp.clipboard", new Object[] { component }));
      }
    }
  }

  private static Component IPComponent(String Type, String IP) {
    return ComponentUtils.wrapInSquareBrackets(Component.literal(Type)
        .withStyle(style -> style.withColor(ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, IP))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.translatable("chat.copy.click").append("\n").append(IP)))
            .withInsertion(IP)));

  }

  public static Map<String, String> GetGlobalIPv4() {
    String ipv4 = null;
    try {
      URL url = new URL("https://api-ipv4.ip.sb/ip");
      URLConnection URLconnection = url.openConnection();
      InputStreamReader isr = new InputStreamReader(URLconnection.getInputStream());
      BufferedReader bufr = new BufferedReader(isr);
      String str;
      while ((str = bufr.readLine()) != null) {
        ipv4 = str;
      }
      bufr.close();
    } catch (Exception e) {
    }
    Map<String, String> Gl4Map = new HashMap<String, String>();
    if (ipv4 != null) {
      // Gl4Map.put("Iface", "Global IPv4");
      Gl4Map.put("Type", "IPv4");
      Gl4Map.put("Local", "mcwifipnp.gui.Global");
      Gl4Map.put("IP", ipv4);
    }
    return Gl4Map;
  }

  public static Map<String, String> GetGlobalIPv6() {
    String ipv6 = null;
    try {
      URL url = new URL("https://api-ipv6.ip.sb/ip");
      URLConnection URLconnection = url.openConnection();
      InputStreamReader isr = new InputStreamReader(URLconnection.getInputStream());
      BufferedReader bufr = new BufferedReader(isr);
      String str;
      while ((str = bufr.readLine()) != null) {
        ipv6 = str;
      }
      bufr.close();
    } catch (Exception e) {
    }
    Map<String, String> Gl6Map = new HashMap<String, String>();
    if (ipv6 != null) {
      // Gl6Map.put("Iface", "Global IPv6");
      Gl6Map.put("Type", "IPv6");
      Gl6Map.put("Local", "mcwifipnp.gui.Global");
      Gl6Map.put("IP", ipv6);
    }
    return Gl6Map;
  }

  public static ArrayList<Map<String, String>> IPAddressList() {
    ArrayList<Map<String, String>> out = new ArrayList<Map<String, String>>();
    try {
      Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
      while (ifaces.hasMoreElements()) {
        try {
          NetworkInterface iface = ifaces.nextElement();
          if (!iface.isUp() || iface.isLoopback() || iface.isVirtual() || iface.isPointToPoint()) {
            continue;
          }
          if (iface.getDisplayName().contains("Virtual")
              || iface.getDisplayName().contains("VMware")
              || iface.getDisplayName().contains("VirtualBox")
              || iface.getDisplayName().contains("Bluetooth")
              || iface.getDisplayName().contains("Hyper-V")) {
            continue;
          }
          Enumeration<InetAddress> addrs = iface.getInetAddresses();
          if (addrs == null) {
            continue;
          }
          while (addrs.hasMoreElements()) {
            Map<String, String> NetMap = new HashMap<String, String>();
            // NetMap.put("Iface", iface.getDisplayName());
            InetAddress addr = addrs.nextElement();
            if (addr instanceof Inet4Address) {
              NetMap.put("Type", "IPv4");
            }
            if (addr instanceof Inet6Address) {
              NetMap.put("Type", "IPv6");
            }
            if (addr.isLinkLocalAddress()) {
              continue;
            }
            if (addr.isSiteLocalAddress()) {
              NetMap.put("Local", "mcwifipnp.gui.Local");
            } else {
              NetMap.put("Local", "mcwifipnp.gui.Global");
            }
            NetMap.put("IP", addr.getHostAddress());
            out.add(NetMap);
          }
        } catch (Throwable t) {
        }
      }
    } catch (Throwable t) {
    }
    return out;
  }

  protected static boolean convertOldUsers(MinecraftServer server) {
    int i;
    boolean bl = false;
    for (i = 0; !bl && i <= 2; ++i) {
      if (i > 0) {
        LOGGER.warn("Encountered a problem while converting the user banlist, retrying in a few seconds");
        MCWiFiPnPUnit.waitForRetry();
      }
      bl = OldUsersConverter.convertUserBanlist(server);
    }
    boolean bl2 = false;
    for (i = 0; !bl2 && i <= 2; ++i) {
      if (i > 0) {
        LOGGER.warn("Encountered a problem while converting the ip banlist, retrying in a few seconds");
        MCWiFiPnPUnit.waitForRetry();
      }
      bl2 = OldUsersConverter.convertIpBanlist(server);
    }
    boolean bl3 = false;
    for (i = 0; !bl3 && i <= 2; ++i) {
      if (i > 0) {
        LOGGER.warn("Encountered a problem while converting the op list, retrying in a few seconds");
        MCWiFiPnPUnit.waitForRetry();
      }
      bl3 = OldUsersConverter.convertOpsList(server);
    }
    boolean bl4 = false;
    for (i = 0; !bl4 && i <= 2; ++i) {
      if (i > 0) {
        LOGGER.warn("Encountered a problem while converting the whitelist, retrying in a few seconds");
        MCWiFiPnPUnit.waitForRetry();
      }
      bl4 = OldUsersConverter.convertWhiteList(server);
    }
    return bl || bl2 || bl3 || bl4;
  }

  private static void waitForRetry() {
    try {
      Thread.sleep(5000L);
    } catch (InterruptedException interruptedException) {
      return;
    }
  }

}