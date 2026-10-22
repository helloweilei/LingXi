/** 采集到的原始音频按 AudioWorklet 的 128 帧块回传，主线程累积后统一重采样 */
const WORKLET_SOURCE = `
class PcmTap extends AudioWorkletProcessor {
  process(inputs) {
    const ch = inputs[0] && inputs[0][0];
    if (ch) this.port.postMessage(ch.slice(0));
    return true;
  }
}
registerProcessor('pcm-tap', PcmTap);
`;

const TARGET_RATE = 16000;

/**
 * 麦克风录音器：输出云端识别服务通用的 16kHz / 16bit / 单声道 / 小端 PCM。
 */
export class PcmRecorder {
  private ctx: AudioContext | null = null;
  private stream: MediaStream | null = null;
  private chunks: Float32Array[] = [];
  private sampleCount = 0;

  /** 已录制时长（秒） */
  get seconds(): number {
    return this.ctx ? this.sampleCount / this.ctx.sampleRate : 0;
  }

  async start(): Promise<void> {
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
    });
    const ctx = new AudioContext();
    this.ctx = ctx;
    const url = URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: 'application/javascript' }));
    try {
      await ctx.audioWorklet.addModule(url);
    } finally {
      URL.revokeObjectURL(url);
    }
    const source = ctx.createMediaStreamSource(this.stream);
    const tap = new AudioWorkletNode(ctx, 'pcm-tap');
    tap.port.onmessage = (e: MessageEvent<Float32Array>) => {
      this.chunks.push(e.data);
      this.sampleCount += e.data.length;
    };
    // 节点需接入渲染图才会被持续调度；经静音增益接到输出，避免回放麦克风声音
    const mute = ctx.createGain();
    mute.gain.value = 0;
    source.connect(tap);
    tap.connect(mute);
    mute.connect(ctx.destination);
  }

  /** 停止录音并返回 16k PCM；未录到声音时返回空 buffer */
  async stop(): Promise<ArrayBuffer> {
    const rate = this.ctx?.sampleRate ?? TARGET_RATE;
    this.release();
    const merged = new Float32Array(this.sampleCount);
    let offset = 0;
    for (const c of this.chunks) {
      merged.set(c, offset);
      offset += c.length;
    }
    this.chunks = [];
    this.sampleCount = 0;
    return toPcm16(downsample(merged, rate, TARGET_RATE));
  }

  /** 丢弃录音并释放麦克风 */
  cancel(): void {
    this.release();
    this.chunks = [];
    this.sampleCount = 0;
  }

  private release(): void {
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    this.ctx?.close().catch(() => undefined);
    this.ctx = null;
  }
}

/** 区间平均降采样：比直接抽点更少混叠 */
function downsample(input: Float32Array, from: number, to: number): Float32Array {
  if (from === to) {
    return input;
  }
  const ratio = from / to;
  const out = new Float32Array(Math.floor(input.length / ratio));
  for (let i = 0; i < out.length; i++) {
    const start = Math.floor(i * ratio);
    const end = Math.min(input.length, Math.floor((i + 1) * ratio));
    let sum = 0;
    for (let j = start; j < end; j++) {
      sum += input[j];
    }
    out[i] = end > start ? sum / (end - start) : 0;
  }
  return out;
}

function toPcm16(input: Float32Array): ArrayBuffer {
  const view = new DataView(new ArrayBuffer(input.length * 2));
  for (let i = 0; i < input.length; i++) {
    const s = Math.max(-1, Math.min(1, input[i]));
    view.setInt16(i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return view.buffer;
}
