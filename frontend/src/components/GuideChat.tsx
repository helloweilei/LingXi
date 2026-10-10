import { useEffect, useRef, useState } from 'react';
import { Button } from 'antd';
import { generateProposalStream } from '../api/guide';

interface Props {
  intentId: number;
  onDone: () => void;
}

/**
 * 生成创意提案时的流式可视化：复用与 AI 抽屉一致的打字机效果。
 * 完成后由父组件拉取创意方案列表。
 */
export default function GuideChat({ intentId, onDone }: Props) {
  const [text, setText] = useState('');
  const [status, setStatus] = useState<'streaming' | 'done' | 'error'>('streaming');
  const [err, setErr] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const onDoneRef = useRef(onDone);
  onDoneRef.current = onDone;

  useEffect(() => {
    const c = new AbortController();
    abortRef.current = c;
    setText('');
    setErr('');
    setStatus('streaming');
    generateProposalStream(
      intentId,
      {
        onDelta: (d) => setText((p) => p + d),
        onDone: () => {
          setStatus('done');
          onDoneRef.current();
        },
        onError: (m) => {
          setErr(m);
          setStatus('error');
          onDoneRef.current();
        },
      },
      c.signal
    ).catch(() => {
      setStatus('done');
      onDoneRef.current();
    });
    return () => c.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intentId]);

  return (
    <div>
      <div
        className="ai-stream-box"
        style={{ minHeight: 160, maxHeight: 380, overflowY: 'auto', whiteSpace: 'pre-wrap' }}
      >
        {text}
        {status === 'streaming' && <span className="cursor-blink">&nbsp;</span>}
        {status === 'error' && <span style={{ color: '#a8071a' }}>{err}</span>}
        {status === 'done' && !text && <span className="muted">未生成内容，可重新生成</span>}
      </div>
      {status === 'streaming' && (
        <div style={{ marginTop: 12 }}>
          <Button size="small" onClick={() => abortRef.current?.abort()}>
            停止
          </Button>
        </div>
      )}
    </div>
  );
}
