import { useEffect, useState } from 'react';
import { Alert, Form, Input, Modal, Radio, Tag, message } from 'antd';
import { speechApi } from '../api';
import type { SpeechConfigVO, SpeechProvider } from '../api/types';
import { resetSpeechProviderCache } from '../utils/speechProvider';

interface Props {
  open: boolean;
  onClose: () => void;
}

interface FormValues {
  provider: SpeechProvider;
  appId?: string;
  apiKey?: string;
  apiSecret?: string;
}

/**
 * 用户级语音识别配置：填写科大讯飞或阿里云密钥后，语音输入走对应云服务；否则使用浏览器自带识别。
 */
export default function SpeechSettingsModal({ open, onClose }: Props) {
  const [form] = Form.useForm<FormValues>();
  const [saved, setSaved] = useState<SpeechConfigVO | null>(null);
  const [saving, setSaving] = useState(false);
  const provider = Form.useWatch('provider', form);

  useEffect(() => {
    if (!open) {
      return;
    }
    speechApi
      .getConfig()
      .then((cfg) => {
        setSaved(cfg);
        form.setFieldsValue({ provider: cfg.provider, appId: cfg.appId, apiKey: '', apiSecret: '' });
      })
      .catch((e: Error) => message.error(e?.message ?? '读取配置失败'));
  }, [open, form]);

  // 只有服务商未变时，留空的密钥才表示「沿用已保存的值」
  const sameProvider = !!saved && !!provider && saved.provider === provider;
  const keepKey = sameProvider && !!saved?.hasKey;
  const keepSecret = sameProvider && !!saved?.hasSecret;

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await speechApi.saveConfig({
        provider: values.provider,
        appId: values.provider === 'xfyun' ? values.appId?.trim() : '',
        apiKey: values.apiKey?.trim(),
        apiSecret: values.provider === 'xfyun' ? values.apiSecret?.trim() : '',
      });
      resetSpeechProviderCache();
      message.success('语音服务配置已保存');
      onClose();
    } catch (e) {
      message.error((e as Error)?.message ?? '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const keyRule = (keep: boolean, label: string) =>
    keep ? [] : [{ required: true, message: `请输入${label}` }];

  return (
    <Modal
      title="语音服务"
      open={open}
      onCancel={onClose}
      onOk={save}
      okText="保存"
      confirmLoading={saving}
      width={560}
      destroyOnHidden
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="配置科大讯飞或阿里云后，语音输入使用对应云服务识别，准确率更高；未配置时使用浏览器自带语音识别（推荐 Chrome / Edge）。密钥只保存在服务端，仅对当前账号生效。"
      />
      <Form form={form} layout="vertical" initialValues={{ provider: 'browser' }}>
        <Form.Item name="provider" label="识别服务">
          <Radio.Group
            optionType="button"
            buttonStyle="solid"
            options={[
              { value: 'browser', label: '浏览器自带' },
              { value: 'xfyun', label: '科大讯飞' },
              { value: 'aliyun', label: '阿里云' },
            ]}
          />
        </Form.Item>
        {provider === 'xfyun' && (
          <>
            <div className="muted" style={{ fontSize: 12, marginBottom: 12 }}>
              在讯飞开放平台控制台开通「语音听写（流式版）」，从应用详情页复制以下三项。
            </div>
            <Form.Item name="appId" label="APPID" rules={[{ required: true, message: '请输入 APPID' }]}>
              <Input autoComplete="off" />
            </Form.Item>
            <Form.Item name="apiKey" label="APIKey" rules={keyRule(keepKey, 'APIKey')}>
              <Input.Password
                autoComplete="off"
                placeholder={keepKey ? `已配置（${saved?.apiKeyMask}），留空则保持不变` : ''}
              />
            </Form.Item>
            <Form.Item name="apiSecret" label="APISecret" rules={keyRule(keepSecret, 'APISecret')}>
              <Input.Password autoComplete="off" placeholder={keepSecret ? '已配置，留空则保持不变' : ''} />
            </Form.Item>
          </>
        )}
        {provider === 'aliyun' && (
          <>
            <div className="muted" style={{ fontSize: 12, marginBottom: 12 }}>
              使用阿里云百炼（DashScope）的 API Key，识别模型为 Paraformer 实时语音识别。
            </div>
            <Form.Item name="apiKey" label="百炼 API Key" rules={keyRule(keepKey, 'API Key')}>
              <Input.Password
                autoComplete="off"
                placeholder={keepKey ? `已配置（${saved?.apiKeyMask}），留空则保持不变` : 'sk-...'}
              />
            </Form.Item>
          </>
        )}
        {saved && saved.provider !== 'browser' && (
          <Tag color="green">
            当前生效：{saved.provider === 'xfyun' ? '科大讯飞' : '阿里云'}
          </Tag>
        )}
      </Form>
    </Modal>
  );
}
