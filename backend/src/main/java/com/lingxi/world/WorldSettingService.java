package com.lingxi.world;

import com.lingxi.common.BizException;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import com.lingxi.world.dto.WorldDtos.SaveReq;
import com.lingxi.world.dto.WorldDtos.VO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorldSettingService {

    private final WorldSettingRepository worldSettingRepository;
    private final NovelService novelService;

    public WorldSettingService(WorldSettingRepository worldSettingRepository, NovelService novelService) {
        this.worldSettingRepository = worldSettingRepository;
        this.novelService = novelService;
    }

    @Transactional(readOnly = true)
    public List<VO> list(Long novelId, User user) {
        novelService.loadOwned(novelId, user);
        return worldSettingRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novelId)
                .stream().map(WorldSettingService::toVO).toList();
    }

    @Transactional
    public VO create(Long novelId, User user, SaveReq req) {
        novelService.loadOwned(novelId, user);
        WorldSetting w = new WorldSetting();
        w.setNovelId(novelId);
        w.setTitle(req.title());
        w.setContent(req.content());
        worldSettingRepository.save(w);
        return toVO(w);
    }

    @Transactional
    public VO update(Long id, User user, SaveReq req) {
        WorldSetting w = loadOwned(id, user);
        w.setTitle(req.title());
        if (req.content() != null) {
            w.setContent(req.content());
        }
        return toVO(w);
    }

    @Transactional
    public void delete(Long id, User user) {
        worldSettingRepository.delete(loadOwned(id, user));
    }

    private WorldSetting loadOwned(Long id, User user) {
        WorldSetting w = worldSettingRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "设定条目不存在", 404));
        novelService.loadOwned(w.getNovelId(), user);
        return w;
    }

    private static VO toVO(WorldSetting w) {
        return new VO(w.getId(), w.getNovelId(), w.getTitle(), w.getContent(), w.getCreatedAt());
    }
}
