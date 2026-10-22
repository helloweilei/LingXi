import { useEffect, useRef, useState } from 'react';
import { Button, Tooltip, message } from 'antd';
import { AudioOutlined, LoadingOutlined } from '@ant-design/icons';
import { speechApi } from '../api';
import type { SpeechProvider } from '../api/types';
import { PcmRecorder } from '../utils/pcmRecorder';
import { loadSpeechProvider } from '../utils/speechProvider';

/** 云端识别单次最长录音（与后端上限一致） */
const MAX_SECONDS = 60;

const PROVIDER_NAME: Record<SpeechProvider, string> = {
  browser: '浏览器语音识别',
  xfyun: '科大讯飞',
  aliyun: '阿里云',
};

/* 浏览器 Web Speech API 的最小类型声明（Chrome / Edge 以 webkit 前缀提供） */
interface RecognitionAlternative {
  transcript: string;
}
interface RecognitionResult {
  readonly isFinal: boolean;
  readonly length: number;
  [index: number]: RecognitionAlternative;
}
interface RecognitionEvent {
  readonly resultIndex: number;
  readonly results: { readonly length: number; [index: number]: RecognitionResult };
}
interface Recognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((e: RecognitionEvent) => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}
type RecognitionCtor = new () => Recognition;

function browserRecognition(): RecognitionCtor | null {
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

const BROWSER_ERRORS: Record<string, string> = {
  'not-allowed': '麦克风权限被拒绝，请在浏览器地址栏允许使用麦克风',
  'service-not-allowed': '浏览器禁止了语音识别服务',
  'audio-capture': '没有检测到麦克风',
  network: '浏览器语音服务连接失败（Chrome 需能访问 Google 服务），可在「语音服务」中配置科大讯飞或阿里云',
  'no-speech': '没有听到声音，请靠近麦克风再试',
};

interface Props {
  /** 识别出的文字（逐段回调，由调用方决定追加还是替换） */
  onText: (text: string) => void;
  disabled?: boolean;
}

/**
 * 语音输入按钮：用户配置了科大讯飞 / 阿里云时录音上传由后端识别，否则使用浏览器自带语音识别。
 */
export default function VoiceInputButton({ onText, disabled }: Props) {
  const [status, setStatus] = useState<'idle' | 'recording' | 'recognizing'>('idle');
  const [provider, setProvider] = useState<SpeechProvider | null>(null);
  const [interim, setInterim] = useState('');
  const [seconds, setSeconds] = useState(0);
  const recognitionRef = useRef<Recognition | null>(null);
  const recorderRef = useRef<PcmRecorder | null>(null);
  const timerRef = useRef<number | null>(null);
  const onTextRef = useRef(onText);
  onTextRef.current = onText;

  const clearTimer = () => {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
  };

  useEffect(
    () => () => {
      clearTimer();
      recognitionRef.current?.abort();
      recorderRef.current?.cancel();
    },
    [],
  );

  const startBrowser = () => {
    const Ctor = browserRecognition();
    if (!Ctor) {
      message.warning('当前浏览器不支持语音识别，请使用 Chrome / Edge，或在「语音服务」中配置科大讯飞或阿里云');
      return;
    }
    const rec = new Ctor();
    rec.lang = 'zh-CN';
    rec.continuous = true;
    rec.interimResults = true;
    rec.onresult = (e) => {
      let pending = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i];
        if (r.isFinal) {
          onTextRef.current(r[0].transcript);
        } else {
          pending += r[0].transcript;
        }
      }
      setInterim(pending);
    };
    rec.onerror = (e) => {
      if (e.error !== 'aborted') {
        message.error(BROWSER_ERRORS[e.error] ?? `语音识别出错：${e.error}`);
      }
    };
    rec.onend = () => {
      recognitionRef.current = null;
      setInterim('');
      setStatus('idle');
    };
    recognitionRef.current = rec;
    rec.start();
    setStatus('recording');
  };

  const stopCloud = async () => {
    const recorder = recorderRef.current;
    if (!recorder) {
      return;
    }
    clearTimer();
    recorderRef.current = null;
    setStatus('recognizing');
    try {
      const pcm = await recorder.stop();
      if (pcm.byteLength < 16000 * 2 * 0.3) {
        message.info('录音太短，请说完一句话再点击停止');
        return;
      }
      const { text } = await speechApi.recognize(pcm);
      if (text) {
        onTextRef.current(text);
      } else {
        message.info('没有识别到内容，请靠近麦克风再试');
      }
    } catch (e) {
      message.error((e as Error)?.message ?? '语音识别失败');
    } finally {
      setStatus('idle');
    }
  };

  const startCloud = async () => {
    const recorder = new PcmRecorder();
    try {
      await recorder.start();
    } catch (e) {
      recorder.cancel();
      const name = (e as DOMException)?.name;
      message.error(
        name === 'NotAllowedError'
          ? '麦克风权限被拒绝，请在浏览器地址栏允许使用麦克风'
          : name === 'NotFoundError'
            ? '没有检测到麦克风'
            : `无法开始录音：${(e as Error)?.message ?? e}`,
      );
      return;
    }
    recorderRef.current = recorder;
    setSeconds(0);
    setStatus('recording');
    timerRef.current = window.setInterval(() => {
      const s = Math.floor(recorder.seconds);
      setSeconds(s);
      if (s >= MAX_SECONDS) {
        message.info(`单次语音最长 ${MAX_SECONDS} 秒，已自动停止`);
        stopCloud();
      }
    }, 250);
  };

  const toggle = async () => {
    if (status === 'recording') {
      if (recognitionRef.current) {
        recognitionRef.current.stop();
      } else {
        stopCloud();
      }
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      message.warning('当前环境无法使用麦克风（需在 https 或 localhost 下访问）');
      return;
    }
    const p = await loadSpeechProvider();
    setProvider(p);
    if (p === 'browser') {
      startBrowser();
    } else {
      await startCloud();
    }
  };

  const recording = status === 'recording';
  const tip = provider ? `语音输入（${PROVIDER_NAME[provider]}）` : '语音输入';

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
      <Tooltip title={recording ? '点击停止' : tip}>
        <Button
          danger={recording}
          type={recording ? 'primary' : 'default'}
          icon={status === 'recognizing' ? <LoadingOutlined /> : <AudioOutlined />}
          disabled={disabled || status === 'recognizing'}
          onClick={toggle}
        >
          {status === 'recognizing'
            ? '识别中'
            : recording
              ? recognitionRef.current
                ? '停止'
                : `停止 ${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
              : '语音输入'}
        </Button>
      </Tooltip>
      {recording && (
        <span className="muted" style={{ fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {interim || '正在聆听…'}
        </span>
      )}
    </span>
  );
}
