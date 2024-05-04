package cs2.dma.tuil;

import cs2.dma.entry.PlayerInfo;
import cs2.dma.main.PlayerAddressUpdateThread;
import vmm.IVmm;
import vmm.IVmmProcess;

import java.util.*;
import java.io.FileReader;
import com.alibaba.fastjson.parser.DefaultJSONParser;

public class GameDataManager {
    private static long dwLocalPlayerPawn = 0x0;
    private static long dwEntityList = 0x0;

    static {
        try {
            System.out.println("[+] Read offsets.json file");

            FileReader reader = new FileReader("offsets.json");
            char[] buf = new char[1024];
            int len = 0;

            StringBuilder sb = new StringBuilder();
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }

            String json = sb.toString();

            reader.close();

            DefaultJSONParser parser = new DefaultJSONParser(json);
            Map<String, String> map = parser.parseObject(Map.class, Long.class);

            dwLocalPlayerPawn += Long.parseLong(map.get("dwLocalPlayerPawn").replace("0x", ""), 16);
            dwEntityList += Long.parseLong(map.get("dwEntityList").replace("0x", ""), 16);

            System.out.println("[+] dwLocalPlayerPawn: " + dwLocalPlayerPawn);
            System.out.println("[+] dwEntityList: " + dwEntityList);

            parser.close();
        } catch (Exception e) {
            System.out.println("[-] Failed to read offsets.json file: " + e.getMessage());
            System.exit(1);
        }
    }

    private String knowMap = "de_ancient,de_dust2,de_inferno,de_mirage,de_nuke,de_overpass,de_vertigo,de_anubis,cs_office,ar_baggage,ar_shoots";
    private static String[] argvMemProcFS = { "", "-device", "FPGA" };

    private static IVmmProcess gameProcess;
    private static MemoryTool memoryTool;

    private static long clientAddress;
    private long EntityList;
    private long LocalPlayerController;
    private long mapNameAddress;
    private static List<PlayerInfo> playerInfoList;

    private String mapName;

    private IVmm vmm;

    // Offsets
    private static long dwGameTypes_mapName = 0x0; // matchmaking.dll dwGameTypes_mapName

    static {
        try {
            FileReader reader = new FileReader("offsets.json");
            char[] buf = new char[1024];
            int len = 0;

            StringBuilder sb = new StringBuilder();
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }

            String json = sb.toString();

            reader.close();

            DefaultJSONParser parser = new DefaultJSONParser(json);
            Map<String, String> map = parser.parseObject(Map.class, Long.class);

            dwGameTypes_mapName += Long.parseLong(map.get("dwGameTypes_mapName").replace("0x", ""), 16);

            System.out.println("[+] dwGameTypes_mapName: " + dwGameTypes_mapName);

            parser.close();
        } catch (Exception e) {
            System.out.println("[-] Failed to read offsets.json file: " + e.getMessage());
            System.exit(1);
        }
    }

    public boolean initializeVmm() {
        this.vmm = IVmm.initializeVmm(System.getProperty("user.dir") + "\\vmm", argvMemProcFS);
        vmm.setConfig(IVmm.VMMDLL_OPT_REFRESH_FREQ_FAST, 1);
        return vmm.isValid();
    }

    public IVmm getVmm() {
        return vmm;
    }

    public String getMapName() {
        return mapName;
    }

    public boolean initializeGameData() {
        List<IVmmProcess> pList = this.vmm.processGetAll();
        for (int i = 0; i < pList.size(); i++) {
            if (pList.get(i).getName().equals("cs2.exe")) {
                gameProcess = pList.get(i);
                break;
            }
        }

        if (gameProcess == null) {
            System.out.println("[-] Failed to find cs2.exe process");
            return false;
        }

        memoryTool = new MemoryTool(gameProcess);
        clientAddress = memoryTool.getModuleAddress("client.dll");
        mapNameAddress = memoryTool.getModuleAddress("matchmaking.dll");
        mapNameAddress = memoryTool.readAddress(mapNameAddress + dwGameTypes_mapName, 8);
        EntityList = memoryTool.readAddress(clientAddress + dwEntityList, 8);
        EntityList = memoryTool.readAddress(EntityList + 0x10, 8);

        initPlayerInfo();
        if (EntityList == 0) {
            return false;
        }

        return true;
    }

    public void initPlayerInfo() {
        mapName = memoryTool.readString(mapNameAddress + 0x4, 32);

        LocalPlayerController = memoryTool.readAddress(clientAddress + dwLocalPlayerPawn, 8);
        if (LocalPlayerController == 0) {
            return;
        }

        List<PlayerInfo> list = new ArrayList<>();
        List<PlayerAddressUpdateThread> pautList = new ArrayList<>();
        boolean isKnowMap = mapName != null && !"".equals(mapName) && knowMap.indexOf(mapName) != -1;

        for (int i = 0; i < 64; i++) {
            PlayerAddressUpdateThread updateThread = new PlayerAddressUpdateThread();
            updateThread.setIndex(i);
            updateThread.setMemoryTool(memoryTool);
            updateThread.setClientAddress(clientAddress);
            updateThread.setEntityList(EntityList);
            updateThread.setDwEntityList(dwEntityList);
            updateThread.setLocalPlayerController(LocalPlayerController);
            updateThread.setKnowMap(isKnowMap);
            updateThread.start();
            pautList.add(updateThread);
        }

        pautList.forEach(pItem -> {
            try {
                pItem.join();
                PlayerInfo data = pItem.getPlayerInfo();
                if (data != null) {
                    list.add(pItem.getPlayerInfo());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        playerInfoList = list;
    }

    public List<PlayerInfo> getPlayerInfoList() {
        return playerInfoList;
    }

}
