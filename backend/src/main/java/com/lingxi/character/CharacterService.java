package com.lingxi.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.ai.AiContext;
import com.lingxi.ai.AiTaskType;
import com.lingxi.ai.LlmClientProvider;
import com.lingxi.character.dto.CharacterDtos.RelationReq;
import com.lingxi.character.dto.CharacterDtos.RelationVO;
import com.lingxi.character.dto.CharacterDtos.SaveReq;
import com.lingxi.character.dto.CharacterDtos.VO;
import com.lingxi.common.BizException;
import com.lingxi.novel.Novel;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CharacterService {

    private static final Logger log = LoggerFactory.getLogger(CharacterService.class);

    private static final Duration INFER_TIMEOUT = Duration.ofMinutes(3);

    private static final String RELATION_SYSTEM_PROMPT = """
            你是「灵犀创作」平台内置的中文网文策划助手，擅长从人物设定与故事大纲中梳理人物关系。请严格遵守：
            1. 只依据用户提供的角色与设定推理，不得编造新角色，人名必须与给出的角色名逐字一致；
            2. 只输出 JSON 数组，不要输出任何解释、前言、结语或 Markdown 代码块标记。
            """;

    private final CharacterRepository characterRepository;
    private final CharacterRelationRepository relationRepository;
    private final NovelService novelService;
    private final LlmClientProvider llmClientProvider;
    private final ObjectMapper objectMapper;

    public CharacterService(CharacterRepository characterRepository, CharacterRelationRepository relationRepository,
                            NovelService novelService, LlmClientProvider llmClientProvider,
                            ObjectMapper objectMapper) {
        this.characterRepository = characterRepository;
        this.relationRepository = relationRepository;
        this.novelService = novelService;
        this.llmClientProvider = llmClientProvider;
        this.objectMapper = objectMapper;
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
        NovelCharacter c = loadOwned(id, user);
        relationRepository.deleteBySourceIdOrTargetId(c.getId(), c.getId());
        characterRepository.delete(c);
    }

    @Transactional(readOnly = true)
    public List<RelationVO> listRelations(Long novelId, User user) {
        novelService.loadOwned(novelId, user);
        return relationRepository.findByNovelIdOrderByIdAsc(novelId)
                .stream().map(CharacterService::toRelationVO).toList();
    }

    @Transactional
    public RelationVO createRelation(Long novelId, User user, RelationReq req) {
        novelService.loadOwned(novelId, user);
        if (req.sourceId().equals(req.targetId())) {
            throw new BizException("不能和自己建立关系");
        }
        requireInNovel(req.sourceId(), novelId);
        requireInNovel(req.targetId(), novelId);
        String label = req.label().trim();
        if (relationRepository.existsByNovelIdAndSourceIdAndTargetIdAndLabel(
                novelId, req.sourceId(), req.targetId(), label)) {
            throw new BizException("该关系已存在");
        }
        CharacterRelation r = new CharacterRelation();
        r.setNovelId(novelId);
        r.setSourceId(req.sourceId());
        r.setTargetId(req.targetId());
        r.setLabel(label);
        relationRepository.save(r);
        return toRelationVO(r);
    }

    @Transactional
    public RelationVO updateRelation(Long id, User user, RelationReq req) {
        CharacterRelation r = loadOwnedRelation(id, user);
        if (req.sourceId().equals(req.targetId())) {
            throw new BizException("不能和自己建立关系");
        }
        requireInNovel(req.sourceId(), r.getNovelId());
        requireInNovel(req.targetId(), r.getNovelId());
        r.setSourceId(req.sourceId());
        r.setTargetId(req.targetId());
        r.setLabel(req.label().trim());
        return toRelationVO(r);
    }

    @Transactional
    public void deleteRelation(Long id, User user) {
        relationRepository.delete(loadOwnedRelation(id, user));
    }

    /**
     * AI 推理人物关系并落库，返回全书最新关系列表。
     * replace=true 时先清空已有关系再写入；否则只追加不重复的关系。
     * 不在方法上开事务：模型调用可能持续数十秒，不应一直占用数据库连接。
     */
    public List<RelationVO> inferRelations(Long novelId, User user, boolean replace) {
        Novel novel = novelService.loadOwned(novelId, user);
        List<NovelCharacter> characters = characterRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novelId);
        if (characters.size() < 2) {
            throw new BizException("至少需要两个角色才能推理关系");
        }
        StringBuilder raw = new StringBuilder();
        AiContext ctx = new AiContext(novel.getTitle(), novel.getGenre(),
                characters.stream().map(NovelCharacter::getName).toList(), null);
        llmClientProvider.forUser(user)
                .stream(AiTaskType.CHARACTER_RELATION, ctx, RELATION_SYSTEM_PROMPT, relationPrompt(novel, characters))
                .doOnNext(raw::append)
                .blockLast(INFER_TIMEOUT);

        List<CharacterRelation> existing = relationRepository.findByNovelIdOrderByIdAsc(novelId);
        if (replace) {
            relationRepository.deleteAll(existing);
            existing = List.of();
        }
        Map<String, Long> idByName = new HashMap<>();
        characters.forEach(c -> idByName.put(c.getName().trim(), c.getId()));
        Set<String> seen = new HashSet<>();
        existing.forEach(r -> seen.add(r.getSourceId() + "|" + r.getTargetId() + "|" + r.getLabel()));

        List<CharacterRelation> toSave = new ArrayList<>();
        for (JsonNode item : parseArray(raw.toString())) {
            Long source = idByName.get(cleanName(item.path("source").asText("")));
            Long target = idByName.get(cleanName(item.path("target").asText("")));
            String label = item.path("label").asText("").trim();
            if (source == null || target == null || source.equals(target) || label.isEmpty()) {
                continue;
            }
            if (label.length() > 30) {
                label = label.substring(0, 30);
            }
            if (!seen.add(source + "|" + target + "|" + label)) {
                continue;
            }
            CharacterRelation r = new CharacterRelation();
            r.setNovelId(novelId);
            r.setSourceId(source);
            r.setTargetId(target);
            r.setLabel(label);
            toSave.add(r);
        }
        relationRepository.saveAll(toSave);
        return relationRepository.findByNovelIdOrderByIdAsc(novelId)
                .stream().map(CharacterService::toRelationVO).toList();
    }

    private String relationPrompt(Novel novel, List<NovelCharacter> characters) {
        StringBuilder sb = new StringBuilder();
        sb.append("作品：《").append(nullToEmpty(novel.getTitle())).append("》");
        if (!nullToEmpty(novel.getGenre()).isBlank()) {
            sb.append("（").append(novel.getGenre()).append("）");
        }
        sb.append("\n");
        if (!nullToEmpty(novel.getDescription()).isBlank()) {
            sb.append("简介：").append(limit(novel.getDescription(), 800)).append("\n");
        }
        if (!nullToEmpty(novel.getOutline()).isBlank()) {
            sb.append("全书大纲（节选）：\n").append(limit(novel.getOutline(), 3000)).append("\n");
        }
        sb.append("\n角色列表：\n");
        for (NovelCharacter c : characters) {
            sb.append("- ").append(c.getName()).append("（").append(nullToEmpty(c.getRole())).append("）：")
                    .append(limit(nullToEmpty(c.getDescription()), 300)).append("\n");
        }
        sb.append("""

                请推理以上角色之间的人物关系：
                1. 输出一个 JSON 数组，每项形如 {"source":"角色名","target":"角色名","label":"关系"}；
                2. 关系是有向的，读作「source 是 target 的 label」，如 {"source":"张三","target":"李四","label":"师父"}；
                3. label 用 2~6 个字概括（如 师徒、宿敌、暗恋、上下级、青梅竹马），要贴合设定，不要泛泛写「认识」；
                4. 只连有依据的关系，每对角色最多 2 条；设定中看不出关系的角色可以不连；
                5. 只输出 JSON，不要任何额外文字。
                """);
        return sb.toString();
    }

    private List<JsonNode> parseArray(String raw) {
        List<JsonNode> list = new ArrayList<>();
        int a = raw.indexOf('[');
        int b = raw.lastIndexOf(']');
        if (a < 0 || b <= a) {
            log.warn("character relation inference returned no JSON array");
            return list;
        }
        try {
            JsonNode node = objectMapper.readTree(raw.substring(a, b + 1));
            if (node.isArray()) {
                node.forEach(list::add);
            }
        } catch (Exception e) {
            log.warn("character relation inference returned invalid JSON: {}", e.toString());
        }
        return list;
    }

    private static String cleanName(String name) {
        return name.replace("「", "").replace("」", "").replace("《", "").replace("》", "").trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String limit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private CharacterRelation loadOwnedRelation(Long id, User user) {
        CharacterRelation r = relationRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "关系不存在", 404));
        novelService.loadOwned(r.getNovelId(), user);
        return r;
    }

    private void requireInNovel(Long characterId, Long novelId) {
        boolean ok = characterRepository.findById(characterId)
                .map(c -> c.getNovelId().equals(novelId))
                .orElse(false);
        if (!ok) {
            throw new BizException(404, "角色不存在", 404);
        }
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

    private static RelationVO toRelationVO(CharacterRelation r) {
        return new RelationVO(r.getId(), r.getSourceId(), r.getTargetId(), r.getLabel());
    }
}
