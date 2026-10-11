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
  // 收到终止事件（done/error）后应立即结束读取：否则 await 可能一直挂到连接关闭，
  // 导致调用方的 finally（如关闭「生成中」弹窗）永远不执行。
  let finished = false;
  // SSE 的 error 事件只是报文，不会让 fetch reject；需显式转成异常，
  // 否则调用方 try/catch 收不到错误，还会误报"生成成功"。
  let streamError: string | null = null;

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
      finished = true;
      handlers.onDone?.();
    } else if (event === 'error') {
      finished = true;
      streamError = data || 'AI 服务暂时不可用';
      handlers.onError?.(streamError);
    }
  };

  try {
    while (!finished) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      const parts = buf.split('\n\n');
      buf = parts.pop() ?? '';
      for (const p of parts) {
        if (p.trim()) handleEvent(p);
      }
    }
    if (!finished && buf.trim()) handleEvent(buf);
  } finally {
    // 提前结束（如已收到 done）：主动取消底层流，避免连接与 reader 泄漏
    if (finished) {
      try {
        await reader.cancel();
      } catch {
        /* ignore */
      }
    }
    reader.releaseLock?.();
  }

  if (streamError) {
    throw new Error(streamError);
  }
}
