import { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Input,
  Modal,
  Progress,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from "antd";
import { CheckOutlined, CloseOutlined, HighlightOutlined } from "@ant-design/icons";
import { streamSse } from "../api/ai";

const { Text } = Typography;

export interface ProofreadIssue {
  /** 原文中逐字一致的错误片段 */
  wrong: string;
  suggestion: string;
  type: string;
  reason: string;
}

const TYPE_COLOR: Record<string, string> = {
  错别字: "red",
  词语误用: "volcano",
  语病: "orange",
  标点: "gold",
  称呼不一致: "purple",
};

/** 从可能带 ```json 包裹或前后缀的模型输出中提取 JSON 数组 */
function extractJsonArray(raw: string): ProofreadIssue[] {
  const text = raw.trim();
  if (!text) return [];
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start < 0 || end <= start) return [];
  try {
    const arr = JSON.parse(text.slice(start, end + 1));
    if (!Array.isArray(arr)) return [];
    return arr
      .filter((x: any) => x && typeof x.wrong === "string" && x.wrong.trim())
      .map((x: any) => ({
        wrong: String(x.wrong),
        suggestion: String(x.suggestion ?? ""),
        type: String(x.type ?? "其他"),
        reason: String(x.reason ?? ""),
      }));
  } catch {
    return [];
  }
}

/**
 * 错别字检查面板：流式校对全文，逐条列出错误并支持「采纳修正」。
 * 采纳时按 wrong 在正文中定位替换（从后往前替换，避免位移影响后续定位）。
 */
export default function ProofreadPanel({
  open,
  content,
  onClose,
  onApplyFix,
}: {
  open: boolean;
  content: string;
  onClose: () => void;
  /** 返回修正后的全文；返回 null 表示未修改 */
  onApplyFix: (next: string) => boolean;
}) {
  const [issues, setIssues] = useState<ProofreadIssue[]>([]);
  const [loading, setLoading] = useState(false);
  const [streamed, setStreamed] = useState("");
  const [applied, setApplied] = useState<Set<string>>(new Set());
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!open) {
      abortRef.current?.abort();
      setIssues([]);
      setStreamed("");
      setApplied(new Set());
      setLoading(false);
    }
  }, [open]);

  const run = useCallback(async () => {
    if (!content.trim()) {
      message.warning("正文为空，无需检查");
      return;
    }
    abortRef.current?.abort();
    const ctl = new AbortController();
    abortRef.current = ctl;
    setLoading(true);
    setIssues([]);
    setStreamed("");
    setApplied(new Set());
    let acc = "";
    try {
      await streamSse(
        "/api/ai/proofread",
        { text: content },
        {
          onDelta: (d) => {
            acc += d;
            setStreamed(acc);
          },
        },
        ctl.signal,
      );
      const list = extractJsonArray(acc);
      setIssues(list);
      if (list.length === 0) {
        message.success("未发现明显错别字");
      }
    } catch (e: any) {
      if (!ctl.signal.aborted) {
        message.error(e?.message ?? "检查失败");
      }
    } finally {
      setLoading(false);
    }
  }, [content]);

  // 打开面板自动开始检查（用 openRef 记录已触发过，避免修正后 content 变化导致重复检查）
  const triggeredRef = useRef(false);
  useEffect(() => {
    if (open && !triggeredRef.current) {
      triggeredRef.current = true;
      void run();
    }
    if (!open) {
      triggeredRef.current = false;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /** 采纳某条修正：替换正文中对应的错误片段 */
  const applyOne = (issue: ProofreadIssue) => {
    if (!content.includes(issue.wrong)) {
      message.warning(`正文中已找不到「${issue.wrong}」`);
      return;
    }
    const idx = content.indexOf(issue.wrong);
    const next = content.slice(0, idx) + issue.suggestion + content.slice(idx + issue.wrong.length);
    const ok = onApplyFix(next);
    if (ok) {
      setApplied((prev) => new Set(prev).add(issue.wrong));
      message.success(`已修正：${issue.wrong} → ${issue.suggestion}`);
    }
  };

  /** 全部采纳：统一应用到当前正文内容 */
  const applyAll = () => {
    let next = content;
    let count = 0;
    // 从后往前替换，保证前面的下标不受影响
    const sorted = [...issues]
      .filter((i) => !applied.has(i.wrong))
      .map((i) => ({ ...i, pos: next.indexOf(i.wrong) }))
      .filter((i) => i.pos >= 0)
      .sort((a, b) => b.pos - a.pos);
    for (const i of sorted) {
      if (next.slice(i.pos, i.pos + i.wrong.length) === i.wrong) {
        next = next.slice(0, i.pos) + i.suggestion + next.slice(i.pos + i.wrong.length);
        count++;
      }
    }
    if (count === 0) {
      message.info("没有可自动替换的条目，请逐条确认");
      return;
    }
    if (onApplyFix(next)) {
      setApplied(new Set(issues.map((i) => i.wrong)));
      message.success(`已采纳 ${count} 条修正`);
    }
  };

  const pending = issues.filter((i) => !applied.has(i.wrong)).length;

  return (
    <Modal
      title={
        <Space>
          <HighlightOutlined />
          错别字检查
        </Space>
      }
      open={open}
      onCancel={onClose}
      width={720}
      footer={
        <Space>
          <Button onClick={run} loading={loading}>
            重新检查
          </Button>
          <Button
            type="primary"
            disabled={pending === 0 || loading}
            onClick={applyAll}
          >
            全部采纳{pending > 0 ? `（${pending}）` : ""}
          </Button>
          <Button onClick={onClose}>关闭</Button>
        </Space>
      }
      destroyOnHidden
    >
      {loading ? (
        <div style={{ padding: "20px 0" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
            <Spin size="small" />
            <span>正在校对全文…（长章节耗时较久）</span>
          </div>
          <Input.TextArea
            readOnly
            value={streamed}
            autoSize={{ minRows: 6, maxRows: 12 }}
            style={{ fontFamily: "inherit", fontSize: 12 }}
          />
        </div>
      ) : issues.length === 0 ? (
        <Empty
          description={
            <span className="muted">
              未发现明显的错别字、词语误用或标点问题
              <br />
              <span style={{ fontSize: 12 }}>
                提示：仅检查用词与标点，不评价文笔；刻意使用的错别字角色与网文梗会被忽略
              </span>
            </span>
          }
          style={{ padding: "30px 0" }}
        />
      ) : (
        <div>
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message={`发现 ${issues.length} 处疑似问题，其中 ${pending} 处待处理`}
          />
          <div style={{ maxHeight: 460, overflowY: "auto" }}>
            {issues.map((it, i) => {
              const done = applied.has(it.wrong);
              return (
                <div
                  key={`${it.wrong}-${i}`}
                  style={{
                    border: "1px solid #eee",
                    borderRadius: 8,
                    padding: 12,
                    marginBottom: 10,
                    opacity: done ? 0.5 : 1,
                    background: done ? "#fafafa" : "#fff",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 8,
                      marginBottom: 6,
                      flexWrap: "wrap",
                    }}
                  >
                    <Tag color={TYPE_COLOR[it.type] ?? "default"}>{it.type}</Tag>
                    <Text delete={done} style={{ color: "#c41d3f" }}>
                      {it.wrong}
                    </Text>
                    {!done && <span>→</span>}
                    {!done && <Text strong style={{ color: "#389e0d" }}>{it.suggestion}</Text>}
                  </div>
                  {it.reason && (
                    <div className="muted" style={{ fontSize: 12, marginBottom: done ? 0 : 8 }}>
                      {it.reason}
                    </div>
                  )}
                  {!done && (
                    <Space size={8}>
                      <Button
                        size="small"
                        type="primary"
                        icon={<CheckOutlined />}
                        onClick={() => applyOne(it)}
                      >
                        采纳
                      </Button>
                      <Button
                        size="small"
                        icon={<CloseOutlined />}
                        onClick={() =>
                          setApplied((prev) => new Set(prev).add(it.wrong))
                        }
                      >
                        忽略
                      </Button>
                    </Space>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </Modal>
  );
}