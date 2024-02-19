package cs2.dma.main;

import com.alibaba.fastjson.JSONObject;
import cs2.dma.entry.PlayerInfo;
import cs2.dma.tuil.GameDataManager;

import org.springframework.stereotype.Controller;

import java.util.Date;
import java.util.List;

@Controller
public class GameDataController {

    private static GameDataManager gameDataManager;

    public static void setGameDataManager(GameDataManager gameDataManager) {
        GameDataController.gameDataManager = gameDataManager;
    }

    public String getGameData() throws Exception {
        Date time = new Date();
        JSONObject gameData = new JSONObject();
        gameDataManager.initPlayerInfo();
        String mapName = gameDataManager.getMapName();
        if ("<empty>".equals(mapName) || "".equals(mapName) || mapName == null) {
            return "";
        }
        List<PlayerInfo> list = gameDataManager.getPlayerInfoList();
        gameData.put("playerList", list);
        gameData.put("mapName", mapName);
        gameData.put("tick", new Date().getTime() - time.getTime());
        return gameData.toJSONString();
    }

}
