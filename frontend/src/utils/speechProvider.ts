import { speechApi } from '../api';
import type { SpeechProvider } from '../api/types';

let providerCache: Promise<SpeechProvider> | null = null;

/** 读取当前用户的语音服务（缓存）；读取失败时回落到浏览器识别 */
export function loadSpeechProvider(): Promise<SpeechProvider> {
  providerCache ??= speechApi
    .getConfig()
    .then((c) => c.provider)
    .catch(() => 'browser' as SpeechProvider);
  return providerCache;
}

/** 语音服务配置变更后调用，下次录音重新读取 */
export function resetSpeechProviderCache() {
  providerCache = null;
}
