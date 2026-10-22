import { useEffect, useRef, useState } from 'react';
import { Button, Drawer, Space, message } from 'antd';
import { CopyOutlined, ReloadOutlined } from '@ant-design/icons';
import { streamSse } from '../api/ai';

export type StreamFn = (handlers: {
  onDelta: (text: string) => void;
  onDone?: () => void;
  onError?: (message: string) => void;
}) => Promise<void>;

interface AiDrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  /** 每次点击“生成/重新生成”都会调用该函数 */
  run: StreamFn;
  /** 应用按钮文案，如“插入到正文”；不传则不显示 */
  applyText?: string;
  onApply?: (text: string) => void;
}

export default function AiDrawer({ open, onClose, title, run, applyText, onApply }: AiDrawerProps) {
  const [text, setText] = useState('');
  const [status, setStatus] = useState<'idle' | 'streaming' | 'done' | 'error'>('idle');
  const [errMsg, setErrMsg] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const runRef = useRef(run);
  runRef.current = run;

  const start = () => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setText('');
    setErrMsg('');
    setStatus('streaming');
    runRef.current({
      onDelta: (d) => setText((prev) => prev + d),
      onDone: () => setStatus((s) => (s === 'streaming' ? 'done' : s)),
      onError: (m) => {
        setErrMsg(m);
        setStatus('error');
      },
    }).catch((e) => {
      if (controller.signal.aborted) {
        setStatus((s) => (s === 'streaming' ? 'done' : s));
        return;
      }
      setErrMsg(e?.message || '请求失败');
      setStatus('error');
    });
  };

  // 打开抽屉时自动开始一次生成；关闭时中断
  useEffect(() => {
    if (open) {
      start();
    } else {
      abortRef.current?.abort();
    }
    return () => abortRef.current?.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const stop = () => {
    abortRef.current?.abort();
    setStatus((s) => (s === 'streaming' ? 'done' : s));
  };

  const copy = async () => {
    await navigator.clipboard.writeText(text);
    message.success('已复制');
  };

  return (
    <Drawer
      title={title}
      open={open}
      onClose={() => {
        abortRef.current?.abort();
        onClose();
      }}
      width={460}
      destroyOnHidden
      extra={
        status === 'streaming' ? (
          <Button size="small" onClick={stop}>
            停止
          </Button>
        ) : (
          <Button size="small" icon={<ReloadOutlined />} onClick={start}>
            重新生成
          </Button>
        )
      }
    >
      <div className="ai-stream-box">
        {text}
        {status === 'streaming' && <span className="cursor-blink">&nbsp;</span>}
        {status === 'idle' && <span className="muted">正在准备生成……</span>}
        {status === 'error' && (
          <span style={{ color: '#a8071a' }}>
            {errMsg}
            {text ? '\n\n（以上为已生成部分，可重新生成）' : ''}
          </span>
        )}
      </div>
      <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Button icon={<CopyOutlined />} disabled={!text || status === 'streaming'} onClick={copy}>
          复制
        </Button>
        {applyText && onApply && (
          <Button
            type="primary"
            disabled={!text || status === 'streaming'}
            onClick={() => {
              onApply(text);
            }}
          >
            {applyText}
          </Button>
        )}
      </div>
    </Drawer>
  );
}
