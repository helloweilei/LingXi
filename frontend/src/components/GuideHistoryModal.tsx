import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Collapse,
  Empty,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from "antd";
import { DeleteOutlined, FileSearchOutlined, PlayCircleOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { guideApi } from "../api/guide";
import type { GuideState, IntentSummary } from "../api/guide";

const { Paragraph, Text } = Typography;

const STATUS_META: Record<string, { color: string; text: string }> = {
  COLLECTING: { color: "blue", text: "收集中" },
  PROPOSING: { color: "processing", text: "已出方案" },
  READY: { color: "green", text: "已生成作品" },
};

/** 问答偏好字段 → 中文标签 */
const ANSWER_LABELS: Record<string, string> = {
  genre: "题材",
  tone: "基调",
  audience: "读者",
  conflict: "冲突",
  length: "篇幅",
  words: "字数",
  platform: "平台",
};

/**
 * 历史创意方案弹窗：列出当前用户的引导会话，可展开查看方案详情并直接采用生成作品。
 * 方案详情按需拉取（guideApi.get），避免一次性传输全部历史内容。
 */
export default function GuideHistoryModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const navigate = useNavigate();
  const [list, setList] = useState<IntentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<Record<number, GuideState>>({});
  const [detailLoading, setDetailLoading] = useState<number | null>(null);
  const [adopting, setAdopting] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setList(await guideApi.history(20));
    } catch (e: any) {
      message.error(e?.message ?? "加载历史方案失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      setDetail({});
      load();
    }
  }, [open, load]);

  /** 展开某个会话时按需加载完整方案 */
  const loadDetail = async (id: number) => {
    if (detail[id]) {
      return;
    }
    setDetailLoading(id);
    try {
      const s = await guideApi.get(id);
      setDetail((prev) => ({ ...prev, [id]: s }));
    } catch (e: any) {
      message.error(e?.message ?? "加载方案详情失败");
    } finally {
      setDetailLoading(null);
    }
  };

  /** 删除某个引导会话（仅删该会话及方案，不影响已生成的作品） */
  const remove = async (intentId: number, name: string) => {
    try {
      await guideApi.remove(intentId);
      setList((prev) => prev.filter((x) => x.id !== intentId));
      setDetail((prev) => {
        const next = { ...prev };
        delete next[intentId];
        return next;
      });
      message.success(`已删除「${name}」`);
    } catch (e: any) {
      message.error(e?.message ?? "删除失败");
    }
  };

  /** 采用某个方案生成作品，成功后跳转到写作页 */
  const adopt = async (intentId: number, index: number, title: string) => {
    const key = `${intentId}-${index}`;
    setAdopting(key);
    try {
      const r = await guideApi.select(intentId, index);
      message.success(`已采用《${title}》，正在进入写作页`);
      onClose();
      navigate(`/novel/${r.novelId}`);
    } catch (e: any) {
      message.error(e?.message ?? "生成作品失败");
    } finally {
      setAdopting(null);
    }
  };

  return (
    <Modal
      title="历史创意方案"
      open={open}
      onCancel={onClose}
      footer={<Button onClick={onClose}>关闭</Button>}
      width={880}
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="这里保留了你历次灵感启发的方案，可直接查看详情或采用生成作品。"
      />
      {loading ? (
        <Spin />
      ) : list.length === 0 ? (
        <Empty description="还没有历史方案" style={{ padding: "30px 0" }} />
      ) : (
        <Collapse
          accordion
          onChange={(key) => {
            const id = Number(Array.isArray(key) ? key[0] : key);
            if (Number.isFinite(id) && id > 0) {
              void loadDetail(id);
            }
          }}
          items={list.map((it) => {
            const st = STATUS_META[it.status] ?? { color: "default", text: it.status };
            const s = detail[it.id];
            // 主标题：已生成作品优先展示作品标题（用户真正拥有的产物）
            const mainTitle =
              it.novelExists && it.novelTitle
                ? it.novelTitle
                : it.selectedId
                  ? (it.proposalTitles[it.selectedId] ?? "未命名方案")
                  : (it.proposalTitles[0] ?? (it.seed ? "我的想法" : "引导会话"));
            const fallbackName = it.proposalTitles[0] ?? (it.seed ? "我的想法" : "引导会话");
            return {
              key: String(it.id),
              label: (
                <Space size={8} wrap>
                  {it.novelExists && it.novelId ? (
                    <a onClick={(e) => { e.stopPropagation(); navigate(`/novel/${it.novelId}`); }}>
                      {mainTitle}
                    </a>
                  ) : (
                    <b>{mainTitle}</b>
                  )}
                  <Tag color={st.color}>{st.text}</Tag>
                  {it.status === "COLLECTING" && (
                    <Button
                      type="primary"
                      ghost
                      size="small"
                      icon={<PlayCircleOutlined />}
                      onClick={(e) => {
                        e.stopPropagation();
                        onClose();
                        navigate(`/guide?intentId=${it.id}`);
                      }}
                    >
                      继续问答
                    </Button>
                  )}
                  {it.proposalCount > 0 && (
                    <span className="muted" style={{ fontSize: 12 }}>
                      {it.proposalCount} 套方案
                    </span>
                  )}
                </Space>
              ),
              extra: (
                <Space size={6} onClick={(e) => e.stopPropagation()}>
                  <span className="muted" style={{ fontSize: 12 }}>
                    {dayjs(it.createdAt).format("YYYY-MM-DD HH:mm")}
                  </span>
                  <Popconfirm
                    title={`删除「${fallbackName}」？`}
                    description="将删除该次问答与方案记录，已用它生成的作品不受影响。"
                    okText="删除"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => remove(it.id, fallbackName)}
                  >
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                    />
                  </Popconfirm>
                </Space>
              ),
              children: (
                <div>
                  {it.novelId && it.novelTitle && (
                    <Alert
                      type={it.novelExists ? "success" : "warning"}
                      showIcon
                      style={{ marginBottom: 10 }}
                      message={
                        it.novelExists ? (
                          <span>
                            已生成作品：
                            <a
                              onClick={(e) => {
                                e.stopPropagation();
                                navigate(`/novel/${it.novelId}`);
                              }}
                            >
                              《{it.novelTitle}》
                            </a>
                          </span>
                        ) : (
                          <span>曾生成作品《{it.novelTitle}》，但该作品已被删除</span>
                        )
                      }
                      action={
                        it.novelExists ? (
                          <Button
                            size="small"
                            onClick={(e) => {
                              e.stopPropagation();
                              navigate(`/novel/${it.novelId}`);
                            }}
                          >
                            去写作
                          </Button>
                        ) : undefined
                      }
                    />
                  )}
                  {detailLoading === it.id ? (
                    <Spin size="small" />
                  ) : s?.proposals?.length ? (
                    <Space direction="vertical" size={12} style={{ width: "100%" }}>
                      {s.proposals.map((p, i) => (
                        <div key={i} style={{ border: "1px solid #eee", borderRadius: 8, padding: 12 }}>
                          <div
                            style={{
                              display: "flex",
                              justifyContent: "space-between",
                              alignItems: "center",
                              gap: 8,
                            }}
                          >
                            <b>{p.title || `方案 ${i + 1}`}</b>
                            <Button
                              size="small"
                              type="primary"
                              icon={<FileSearchOutlined />}
                              loading={adopting === `${it.id}-${i}`}
                              onClick={() => adopt(it.id, i, p.title || `方案 ${i + 1}`)}
                            >
                              采用并生成
                            </Button>
                          </div>
                          {p.platform && (
                            <Tag color="blue" style={{ marginTop: 6 }}>
                              {p.platform}
                            </Tag>
                          )}
                          <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 4 }}>
                            {p.premise}
                          </Paragraph>
                          {p.protagonist && (
                            <p style={{ marginBottom: 4 }}>
                              <Text strong>主角：</Text>
                              {p.protagonist}
                            </p>
                          )}
                          {p.outline && (
                            <Paragraph
                              style={{ marginBottom: 0, whiteSpace: "pre-wrap" }}
                              ellipsis={{ rows: 4, expandable: true }}
                            >
                              {p.outline}
                            </Paragraph>
                          )}
                        </div>
                      ))}
                    </Space>
                  ) : it.proposalCount > 0 ? (
                    <span className="muted">展开以查看方案详情</span>
                  ) : (
                    <Alert
                      type="warning"
                      showIcon
                      message="该会话尚未生成方案"
                      description={
                        it.status === "COLLECTING"
                          ? "可回到「灵感启发」继续这次问答，完成后即可生成方案。"
                          : undefined
                      }
                    />
                  )}
                  {it.seed && (
                    <p className="muted" style={{ marginTop: 8, marginBottom: 0 }}>
                      种子：{it.seed}
                    </p>
                  )}
                  {Object.entries(it.answers ?? {}).filter(([k, v]) => k !== "seed" && v).length > 0 && (
                    <div style={{ marginTop: 8 }}>
                      {Object.entries(it.answers)
                        .filter(([k, v]) => k !== "seed" && v)
                        .map(([k, v]) => (
                          <Tag key={k} style={{ marginBottom: 4 }}>
                            {ANSWER_LABELS[k] ?? k}：{v}
                          </Tag>
                        ))}
                    </div>
                  )}
                </div>
              ),
            };
          })}
        />
      )}
    </Modal>
  );
}