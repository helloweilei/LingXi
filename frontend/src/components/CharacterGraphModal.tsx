import { useCallback, useEffect, useRef, useState } from "react";
import { AutoComplete, Button, Empty, Modal, Popconfirm, Select, Space, Spin, Tag, message } from "antd";
import { PlusOutlined, SaveOutlined, ThunderboltOutlined } from "@ant-design/icons";
import * as d3 from "d3";
import { characterApi } from "../api";
import type { CharacterRelationVO, CharacterVO } from "../api/types";

interface Props {
  open: boolean;
  novelId: number;
  characters: CharacterVO[];
  onClose: () => void;
}

interface GNode extends d3.SimulationNodeDatum {
  id: number;
  name: string;
  role: string;
  description: string | null;
}

interface GLink extends d3.SimulationLinkDatum<GNode> {
  id: number;
  label: string;
  /** 同一对角色之间有多条关系时的弯曲偏移，避免连线重叠 */
  curve: number;
}

const GRAPH_HEIGHT = 460;

const LABEL_PRESETS = [
  "父子", "母女", "兄弟", "姐妹", "师徒", "恋人", "夫妻", "挚友", "闺蜜",
  "同事", "上下级", "盟友", "对手", "宿敌", "暗恋", "主仆", "利用",
];

function roleColor(role: string): string {
  if (role.includes("主角")) return "#d4557f";
  if (role.includes("反派") || role.toUpperCase().includes("BOSS")) return "#e05a4f";
  if (role.includes("配角")) return "#4a7fd4";
  return "#8a6dd6";
}

function nodeRadius(n: GNode): number {
  return n.role.includes("主角") ? 28 : 22;
}

/** 二次贝塞尔曲线的控制点：两端中点沿法线方向偏移 curve */
function controlPoint(s: GNode, t: GNode, curve: number): [number, number] {
  const sx = s.x ?? 0;
  const sy = s.y ?? 0;
  const tx = t.x ?? 0;
  const ty = t.y ?? 0;
  const dx = tx - sx;
  const dy = ty - sy;
  const len = Math.hypot(dx, dy) || 1;
  return [(sx + tx) / 2 - (dy / len) * curve, (sy + ty) / 2 + (dx / len) * curve];
}

function linkPath(l: GLink): string {
  const s = l.source as GNode;
  const t = l.target as GNode;
  const [cx, cy] = controlPoint(s, t, l.curve);
  const tx = t.x ?? 0;
  const ty = t.y ?? 0;
  // 终点退到目标节点圆周外，箭头才不会被圆遮住
  const dx = tx - cx;
  const dy = ty - cy;
  const len = Math.hypot(dx, dy) || 1;
  const back = nodeRadius(t) + 4;
  return `M${s.x ?? 0},${s.y ?? 0} Q${cx},${cy} ${tx - (dx / len) * back},${ty - (dy / len) * back}`;
}

function labelPos(l: GLink): [number, number] {
  const s = l.source as GNode;
  const t = l.target as GNode;
  const [cx, cy] = controlPoint(s, t, l.curve);
  return [
    0.25 * (s.x ?? 0) + 0.5 * cx + 0.25 * (t.x ?? 0),
    0.25 * (s.y ?? 0) + 0.5 * cy + 0.25 * (t.y ?? 0),
  ];
}

function buildLinks(relations: CharacterRelationVO[], ids: Set<number>): GLink[] {
  const valid = relations.filter((r) => ids.has(r.sourceId) && ids.has(r.targetId));
  const groups = new Map<string, CharacterRelationVO[]>();
  valid.forEach((r) => {
    const key = [Math.min(r.sourceId, r.targetId), Math.max(r.sourceId, r.targetId)].join("-");
    groups.set(key, [...(groups.get(key) ?? []), r]);
  });
  const links: GLink[] = [];
  groups.forEach((list) => {
    list.forEach((r, i) => {
      const offset = list.length === 1 ? 0 : (i - (list.length - 1) / 2) * 46;
      // 统一以 id 小的一端为基准方向，反向的边取反，保证同一对的多条边分列两侧
      const curve = r.sourceId < r.targetId ? offset : -offset;
      links.push({ id: r.id, source: r.sourceId, target: r.targetId, label: r.label, curve });
    });
  });
  return links;
}

export default function CharacterGraphModal({ open, novelId, characters, onClose }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [relations, setRelations] = useState<CharacterRelationVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [sourceId, setSourceId] = useState<number>();
  const [targetId, setTargetId] = useState<number>();
  const [label, setLabel] = useState("");
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [inferring, setInferring] = useState(false);
  /** 每次打开弹框最多自动推理一次，避免模型返回空结果时反复触发 */
  const autoInferTried = useRef(false);
  const editRef = useRef<(r: CharacterRelationVO) => void>(() => undefined);

  const resetForm = () => {
    setEditingId(null);
    setSourceId(undefined);
    setTargetId(undefined);
    setLabel("");
  };

  const infer = useCallback(
    async (replace: boolean) => {
      setInferring(true);
      try {
        const list = await characterApi.inferRelations(novelId, replace);
        setRelations(list);
        message.success(list.length > 0 ? `AI 已梳理出 ${list.length} 条人物关系` : "AI 未从现有设定中推理出关系");
      } catch (e) {
        message.error((e as Error)?.message ?? "AI 推理失败");
      } finally {
        setInferring(false);
      }
    },
    [novelId],
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await characterApi.relations(novelId);
      setRelations(list);
      if (list.length === 0 && characters.length >= 2 && !autoInferTried.current) {
        autoInferTried.current = true;
        infer(false);
      }
    } finally {
      setLoading(false);
    }
  }, [novelId, characters.length, infer]);

  useEffect(() => {
    if (open) {
      load();
    } else {
      autoInferTried.current = false;
      resetForm();
    }
  }, [open, load]);

  const startEdit = (r: CharacterRelationVO) => {
    setEditingId(r.id);
    setSourceId(r.sourceId);
    setTargetId(r.targetId);
    setLabel(r.label);
  };
  editRef.current = startEdit;

  useEffect(() => {
    const container = containerRef.current;
    if (!open || !container || characters.length === 0) {
      return;
    }
    const width = container.clientWidth || 880;
    const nodes: GNode[] = characters.map((c) => ({
      id: c.id,
      name: c.name,
      role: c.role ?? "",
      description: c.description,
    }));
    const links = buildLinks(relations, new Set(nodes.map((n) => n.id)));

    const svg = d3
      .select(container)
      .append("svg")
      .attr("width", width)
      .attr("height", GRAPH_HEIGHT)
      .style("display", "block")
      .style("cursor", "grab");

    svg
      .append("defs")
      .append("marker")
      .attr("id", "cg-arrow")
      .attr("viewBox", "0 -5 10 10")
      .attr("refX", 9)
      .attr("refY", 0)
      .attr("markerWidth", 7)
      .attr("markerHeight", 7)
      .attr("orient", "auto")
      .append("path")
      .attr("d", "M0,-5L10,0L0,5")
      .attr("fill", "#a3a3c2");

    const root = svg.append("g");
    svg.call(
      d3
        .zoom<SVGSVGElement, unknown>()
        .scaleExtent([0.3, 3])
        .on("zoom", (e: d3.D3ZoomEvent<SVGSVGElement, unknown>) => {
          root.attr("transform", e.transform.toString());
        }),
    );

    const linkSel = root
      .append("g")
      .selectAll<SVGPathElement, GLink>("path")
      .data(links)
      .join("path")
      .attr("fill", "none")
      .attr("stroke", "#c4c4dc")
      .attr("stroke-width", 1.6)
      .attr("marker-end", "url(#cg-arrow)");

    const labelSel = root
      .append("g")
      .selectAll<SVGTextElement, GLink>("text")
      .data(links)
      .join("text")
      .text((d) => d.label)
      .attr("font-size", 12)
      .attr("fill", "#5b5b7a")
      .attr("text-anchor", "middle")
      .attr("dominant-baseline", "middle")
      .attr("stroke", "#fff")
      .attr("stroke-width", 4)
      .attr("paint-order", "stroke")
      .style("cursor", "pointer")
      .on("click", (_e, d) => {
        const r = relations.find((x) => x.id === d.id);
        if (r) editRef.current(r);
      });
    labelSel.append("title").text("点击编辑该关系");

    const nodeSel = root
      .append("g")
      .selectAll<SVGGElement, GNode>("g")
      .data(nodes)
      .join("g")
      .style("cursor", "pointer");

    nodeSel
      .append("circle")
      .attr("r", nodeRadius)
      .attr("fill", (d) => roleColor(d.role))
      .attr("stroke", "#fff")
      .attr("stroke-width", 3);
    nodeSel
      .append("text")
      .text((d) => d.name.slice(0, 1))
      .attr("text-anchor", "middle")
      .attr("dominant-baseline", "central")
      .attr("fill", "#fff")
      .attr("font-size", (d) => (d.role.includes("主角") ? 18 : 15))
      .attr("font-weight", 600);
    nodeSel
      .append("text")
      .text((d) => d.name)
      .attr("text-anchor", "middle")
      .attr("y", (d) => nodeRadius(d) + 16)
      .attr("font-size", 13)
      .attr("fill", "#2f2f45")
      .attr("stroke", "#fff")
      .attr("stroke-width", 3)
      .attr("paint-order", "stroke");
    nodeSel.append("title").text((d) => `${d.name}（${d.role}）\n${d.description ?? ""}`);

    // 悬停时突出该角色及其直接关系
    nodeSel
      .on("mouseenter", (_e, d) => {
        const related = new Set<number>([d.id]);
        links.forEach((l) => {
          const s = (l.source as GNode).id;
          const t = (l.target as GNode).id;
          if (s === d.id) related.add(t);
          if (t === d.id) related.add(s);
        });
        const hit = (l: GLink) => (l.source as GNode).id === d.id || (l.target as GNode).id === d.id;
        nodeSel.attr("opacity", (n) => (related.has(n.id) ? 1 : 0.2));
        linkSel.attr("opacity", (l) => (hit(l) ? 1 : 0.1)).attr("stroke", (l) => (hit(l) ? "#6a5acd" : "#c4c4dc"));
        labelSel.attr("opacity", (l) => (hit(l) ? 1 : 0.1));
      })
      .on("mouseleave", () => {
        nodeSel.attr("opacity", 1);
        linkSel.attr("opacity", 1).attr("stroke", "#c4c4dc");
        labelSel.attr("opacity", 1);
      });

    const simulation = d3
      .forceSimulation<GNode>(nodes)
      .force(
        "link",
        d3
          .forceLink<GNode, GLink>(links)
          .id((d) => d.id)
          .distance(170),
      )
      .force("charge", d3.forceManyBody<GNode>().strength(-520))
      .force("center", d3.forceCenter(width / 2, GRAPH_HEIGHT / 2))
      .force("collide", d3.forceCollide<GNode>().radius((d) => nodeRadius(d) + 26))
      .force("x", d3.forceX<GNode>(width / 2).strength(0.04))
      .force("y", d3.forceY<GNode>(GRAPH_HEIGHT / 2).strength(0.06));

    nodeSel.call(
      d3
        .drag<SVGGElement, GNode>()
        .on("start", (e: d3.D3DragEvent<SVGGElement, GNode, GNode>, d) => {
          if (!e.active) simulation.alphaTarget(0.3).restart();
          d.fx = d.x;
          d.fy = d.y;
        })
        .on("drag", (e: d3.D3DragEvent<SVGGElement, GNode, GNode>, d) => {
          d.fx = e.x;
          d.fy = e.y;
        })
        .on("end", (e: d3.D3DragEvent<SVGGElement, GNode, GNode>, d) => {
          if (!e.active) simulation.alphaTarget(0);
          d.fx = null;
          d.fy = null;
        }),
    );

    simulation.on("tick", () => {
      linkSel.attr("d", linkPath);
      labelSel.each(function (d) {
        const [x, y] = labelPos(d);
        d3.select(this).attr("x", x).attr("y", y);
      });
      nodeSel.attr("transform", (d) => `translate(${d.x ?? 0},${d.y ?? 0})`);
    });

    return () => {
      simulation.stop();
      svg.remove();
    };
  }, [open, characters, relations]);

  const save = async () => {
    if (!sourceId || !targetId || !label.trim()) {
      message.warning("请选择两个角色并填写关系");
      return;
    }
    if (sourceId === targetId) {
      message.warning("不能和自己建立关系");
      return;
    }
    const req = { sourceId, targetId, label: label.trim() };
    setSaving(true);
    try {
      if (editingId) {
        const updated = await characterApi.updateRelation(editingId, req);
        setRelations((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
        message.success("已更新关系");
        resetForm();
      } else {
        const created = await characterApi.createRelation(novelId, req);
        setRelations((prev) => [...prev, created]);
        message.success("已添加关系");
        setLabel("");
      }
    } catch (e) {
      message.error((e as Error)?.message ?? "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: number) => {
    try {
      await characterApi.removeRelation(id);
      setRelations((prev) => prev.filter((r) => r.id !== id));
      if (editingId === id) {
        resetForm();
      }
    } catch (e) {
      message.error((e as Error)?.message ?? "删除失败");
    }
  };

  const nameOf = new Map(characters.map((c) => [c.id, c.name]));
  const options = characters.map((c) => ({ value: c.id, label: c.name }));
  const keyword = label.trim();
  const labelOptions = Array.from(new Set([...relations.map((r) => r.label), ...LABEL_PRESETS]))
    .filter((p) => p.includes(keyword) && p !== keyword)
    .map((p) => ({ value: p }));

  return (
    <Modal title="人物关系图谱" open={open} onCancel={onClose} footer={null} width={960} destroyOnHidden>
      {characters.length === 0 ? (
        <Empty description="还没有角色，先新增角色再来梳理关系" />
      ) : (
        <>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
            <span className="muted" style={{ fontSize: 12 }}>
              滚轮缩放、拖动画布平移，可拖拽角色；悬停角色查看其直接关系，点击连线上的关系名可编辑
            </span>
            {relations.length > 0 ? (
              <Popconfirm
                title="重新推理将覆盖现有的全部关系（包括手动添加的），确定继续？"
                okText="覆盖并推理"
                onConfirm={() => infer(true)}
              >
                <Button icon={<ThunderboltOutlined />} loading={inferring}>
                  AI 重新推理
                </Button>
              </Popconfirm>
            ) : (
              <Button
                icon={<ThunderboltOutlined />}
                loading={inferring}
                disabled={characters.length < 2}
                onClick={() => infer(false)}
              >
                AI 推理关系
              </Button>
            )}
          </div>
          <Spin spinning={loading || inferring} tip={inferring ? "AI 正在根据角色设定推理人物关系…" : undefined}>
            <div
              ref={containerRef}
              style={{ width: "100%", height: GRAPH_HEIGHT, background: "#fafaff", borderRadius: 8 }}
            />
          </Spin>
          <div style={{ height: 14 }} />
          <Space wrap style={{ marginBottom: 12 }}>
            <Select
              placeholder="角色"
              style={{ width: 150 }}
              options={options}
              value={sourceId}
              onChange={setSourceId}
              showSearch
              optionFilterProp="label"
            />
            <span className="muted">是</span>
            <Select
              placeholder="关联角色"
              style={{ width: 150 }}
              options={options}
              value={targetId}
              onChange={setTargetId}
              showSearch
              optionFilterProp="label"
            />
            <span className="muted">的</span>
            <AutoComplete
              placeholder="关系，可自定义"
              style={{ width: 180 }}
              value={label}
              onChange={setLabel}
              maxLength={30}
              options={labelOptions}
            />
            <Button
              type="primary"
              icon={editingId ? <SaveOutlined /> : <PlusOutlined />}
              loading={saving}
              onClick={save}
            >
              {editingId ? "保存修改" : "添加关系"}
            </Button>
            {editingId && <Button onClick={resetForm}>取消编辑</Button>}
          </Space>
          <div>
            {relations.length === 0 ? (
              <span className="muted">暂无关系，可让 AI 推理，或在上方手动添加</span>
            ) : (
              relations.map((r) => (
                <Tag
                  key={r.id}
                  closable
                  color={r.id === editingId ? "purple" : undefined}
                  style={{ marginBottom: 8, cursor: "pointer" }}
                  onClick={() => startEdit(r)}
                  onClose={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    remove(r.id);
                  }}
                >
                  {nameOf.get(r.sourceId) ?? "?"} → {r.label} → {nameOf.get(r.targetId) ?? "?"}
                </Tag>
              ))
            )}
          </div>
        </>
      )}
    </Modal>
  );
}
