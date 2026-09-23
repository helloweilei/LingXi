package com.lingxi.character;

import com.lingxi.character.dto.CharacterDtos.SaveReq;
import com.lingxi.character.dto.CharacterDtos.VO;
import com.lingxi.common.BizException;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final NovelService novelService;

    public CharacterService(CharacterRepository characterRepository, NovelService novelService) {
        this.characterRepository = characterRepository;
        this.novelService = novelService;
    }

    @Transactional(readOnly = true)
    public List<VO> list(Long novelId, User user) {
        novelService.loadOwned(novelId, user);
        return characterRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novelId)
                .stream().map(CharacterService::toVO).toList();
    }

    @Transactional
    public VO create(Long novelId, User user, SaveReq req) {
        novelService.loadOwned(novelId, user);
        NovelCharacter c = new NovelCharacter();
        c.setNovelId(novelId);
        c.setName(req.name());
        c.setRole(req.role() == null || req.role().isBlank() ? "配角" : req.role());
        c.setDescription(req.description());
        characterRepository.save(c);
        return toVO(c);
    }

    @Transactional
    public VO update(Long id, User user, SaveReq req) {
        NovelCharacter c = loadOwned(id, user);
        c.setName(req.name());
        if (req.role() != null) {
            c.setRole(req.role());
        }
        if (req.description() != null) {
            c.setDescription(req.description());
        }
        return toVO(c);
    }

    @Transactional
    public void delete(Long id, User user) {
        characterRepository.delete(loadOwned(id, user));
    }

    private NovelCharacter loadOwned(Long id, User user) {
        NovelCharacter c = characterRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "角色不存在", 404));
        novelService.loadOwned(c.getNovelId(), user);
        return c;
    }

    private static VO toVO(NovelCharacter c) {
        return new VO(c.getId(), c.getNovelId(), c.getName(), c.getRole(),
                c.getDescription(), c.getCreatedAt());
    }
}
