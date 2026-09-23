import { useAuth } from '../store/auth';

export interface SseHandlers {
  onDelta: (text: string) => void;
  onDone?: () => void;
  onError?: (message: string) => void;
}

/**
 * POST + SSE 流式请求：解析 event: delta/done/error。
 * EventSource 不支持 POST 与自定义请求头，因此用 fetch + ReadableStream。
 */
export async function streamSse(
  url: string,
  body: unknown | null,
  handlers: SseHandlers,
  signal?: AbortSignal
): Promise<void> {
  const token = useAuth.getState().token;
  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body == null ? undefined : JSON.stringify(body),
    signal,
  });
  if (!resp.ok || !resp.body) {
    let message = `请求失败（${resp.status}）`;
    try {
      const j = await resp.json();
      message = j.message || message;
    } catch {
      /* ignore */
    }
    throw new Error(message);
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buf = '';

  const handleEvent = (raw: string) => {
    let event = 'message';
    const dataLines: string[] = [];
    for (const line of raw.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''));
      }
    }
    const data = dataLines.join('\n');
    if (event === 'delta') {
      handlers.onDelta(data);
    } else if (event === 'done') {
      handlers.onDone?.();
    } else if (event === 'error') {
      handlers.onError?.(data || 'AI 服务暂时不可用');
    }
  };

  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    const parts = buf.split('\n\n');
    buf = parts.pop() ?? '';
    for (const p of parts) {
      if (p.trim()) handleEvent(p);
    }
  }
  if (buf.trim()) handleEvent(buf);
}
