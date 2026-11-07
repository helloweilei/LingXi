package com.lingxi.memory;

import com.lingxi.common.BizException;
import com.lingxi.memory.dto.MemoryDtos.ForeshadowReq;
import com.lingxi.memory.dto.MemoryDtos.ForeshadowStatusReq;
import com.lingxi.memory.dto.MemoryDtos.ForeshadowingVO;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 伏笔账本：跨章节悬念的登记、状态流转与删除。
 *
 * 用途：OPEN 状态条目由 {@link com.lingxi.ai.ContextBuilder} 注入大纲/正文生成的
 * 【未回收伏笔清单】，让距离超过前文回顾窗口的悬念始终在 AI 的视野内。
 * 所有操作先校验属主（越权返回 404），与全书其他资源的口径一致。
 */
@Service
public class ForeshadowingService {

    private static final Set<String> VALID_STATUS = Set.of(
            Foreshadowing.STATUS_OPEN, Foreshadowing.STATUS_RESOLVED, Foreshadowing.STATUS_ABANDONED);

    private final ForeshadowingRepository repository;
    private final NovelService novelService;

    public ForeshadowingService(ForeshadowingRepository repository, NovelService novelService) {
        this.repository = repository;
        this.novelService = novelService;
    }

    @Transactional(readOnly = true)
    public List<ForeshadowingVO> list(Long novelId, User user) {
        novelService.loadOwned(novelId, user);
        return repository.findByNovelIdOrderByPlantedOrderAscIdAsc(novelId).stream()
                .map(ForeshadowingService::toVO)
                .toList();
    }

    @Transactional
    public ForeshadowingVO create(Long novelId, User user, ForeshadowReq req) {
        novelService.loadOwned(novelId, user);
        Foreshadowing f = new Foreshadowing();
        f.setNovelId(novelId);
        f.setTitle(requireTitle(req));
        f.setContent(req.content() == null || req.content().isBlank() ? null : req.content().trim());
        f.setPlantedOrder(req.plantedOrder());
        f.setExpectedOrder(req.expectedOrder());
        f.setStatus(Foreshadowing.STATUS_OPEN);
        return toVO(repository.save(f));
    }

    /** 编辑：title/content/plantedOrder/expectedOrder 传 null 表示不改该字段 */
    @SuppressWarnings("null")
    @Transactional
    public void update(Long novelId, Long foreshadowId, User user, ForeshadowReq req) {
        novelService.loadOwned(novelId, user);
        Foreshadowing f = loadOwned(novelId, foreshadowId);
        if (req.title() != null && !req.title().isBlank()) {
            f.setTitle(req.title().trim());
        }
        if (req.content() != null) {
            f.setContent(req.content().isBlank() ? null : req.content().trim());
        }
        if (req.plantedOrder() != null) {
            f.setPlantedOrder(req.plantedOrder());
        }
        if (req.expectedOrder() != null) {
            f.setExpectedOrder(req.expectedOrder());
        }
        repository.save(f);
    }

    /** 状态流转：OPEN → RESOLVED（回填实际回收章）/ ABANDONED；也可重新打开 */
    @SuppressWarnings("null")
    @Transactional
    public void changeStatus(Long novelId, Long foreshadowId, User user, ForeshadowStatusReq req) {
        novelService.loadOwned(novelId, user);
        String status = req == null ? null : req.status();
        if (status == null || !VALID_STATUS.contains(status)) {
            throw new BizException(400, "状态必须是 OPEN / RESOLVED / ABANDONED 之一", 400);
        }
        Foreshadowing f = loadOwned(novelId, foreshadowId);
        f.setStatus(status);
        f.setResolvedOrder(Foreshadowing.STATUS_RESOLVED.equals(status) ? req.resolvedOrder() : null);
        repository.save(f);
    }

    @SuppressWarnings("null")
    @Transactional
    public void remove(Long novelId, Long foreshadowId, User user) {
        novelService.loadOwned(novelId, user);
        repository.delete(loadOwned(novelId, foreshadowId));
    }

    private Foreshadowing loadOwned(Long novelId, Long foreshadowId) {
        @SuppressWarnings("null")
        Foreshadowing f = repository.findById(foreshadowId)
                .orElseThrow(() -> new BizException(404, "伏笔不存在", 404));
        if (!f.getNovelId().equals(novelId)) {
            // 越权访问：与全书其他资源一致，返回 404 而非 403，避免暴露资源存在性
            throw new BizException(404, "伏笔不存在", 404);
        }
        return f;
    }

    private static String requireTitle(ForeshadowReq req) {
        if (req == null || req.title() == null || req.title().isBlank()) {
            throw new BizException(400, "伏笔名不能为空", 400);
        }
        return req.title().trim();
    }

    private static ForeshadowingVO toVO(Foreshadowing f) {
        return new ForeshadowingVO(f.getId(), f.getTitle(), f.getContent(), f.getPlantedOrder(),
                f.getExpectedOrder(), f.getStatus(), f.getResolvedOrder(), f.getUpdatedAt());
    }
}
