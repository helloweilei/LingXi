import { useEffect, useRef, useState } from "react";
import { Button, Empty, Input } from "antd";
import { streamSse } from "../api/ai";
import type { ChatMsg } from "../api/types";

export default function ChatTab({ novelId }: { novelId: number }) {
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [errorAt, setErrorAt] = useState(-1);
  const abortRef = useRef<AbortController | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const msgRef = useRef<ChatMsg[]>([]);
  msgRef.current = messages;

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [messages]);

  const send = async () => {
    const q = input.trim();
    if (!q || sending) {
      return;
    }
    const history = msgRef.current
      .filter((m) => m.content)
      .slice(-10)
      .map((m) => ({ role: m.role, content: m.content }));
    setMessages((prev) => [
      ...prev,
      { role: "user", content: q },
      { role: "assistant", content: "" },
    ]);
    setInput("");
    setSending(true);
    setErrorAt(-1);

    const controller = new AbortController();
    abortRef.current = controller;
    const patchLast = (patch: Partial<ChatMsg>) =>
      setMessages((prev) => {
        const copy = [...prev];
        copy[copy.length - 1] = { ...copy[copy.length - 1], ...patch };
        return copy;
      });

    try {
      await streamSse(
        `/api/ai/novels/${novelId}/chat`,
        { message: q, history },
        {
          onDelta: (d) =>
            patchLast({
              content: msgRef.current[msgRef.current.length - 1].content + d,
            }),
          onDone: () => undefined,
          onError: (m) => {
            setErrorAt(msgRef.current.length - 1);
            patchLast({ content: m });
          },
        },
        controller.signal,
      );
    } catch (e: any) {
      if (!controller.signal.aborted) {
        setErrorAt(msgRef.current.length - 1);
        patchLast({ content: e?.message ?? "请求失败" });
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="section-card">
      <div style={{ marginBottom: 10 }}>
        <b>AI 助手</b>
        <span className="muted" style={{ marginLeft: 10 }}>
          结合本书的书名、简介、大纲与设定回答创作问题
        </span>
      </div>
      <div
        className="chat-list"
        ref={listRef}
        style={{ maxHeight: "46vh", overflow: "auto", minHeight: 200 }}
      >
        {messages.length === 0 && (
          <Empty
            description={
              <span className="muted">
                试试问：“主角的动机怎么设计？”、“第二章怎么开头？”、“帮我想三个反转”
              </span>
            }
            style={{ paddingTop: 30 }}
          />
        )}
        {messages.map((m, i) => (
          <div
            key={i}
            className={`chat-bubble ${m.role}${i === errorAt ? " error" : ""}`}
          >
            {m.content || (sending && i === messages.length - 1 ? "……" : "")}
          </div>
        ))}
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <Input.TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault();
              send();
            }
          }}
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={sending}
        />
        {sending ? (
          <Button
            danger
            onClick={() => {
              abortRef.current?.abort();
              setSending(false);
            }}
          >
            停止
          </Button>
        ) : (
          <Button type="primary" onClick={send} disabled={!input.trim()}>
            发送
          </Button>
        )}
      </div>
    </div>
  );
}
