import { useEffect, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Select, Space, Tag, message } from 'antd';
import { aiApi } from '../api';

interface Props {
  open: boolean;
  onClose: () => void;
  onSaved?: () => void;
}

/**
 * 用户级 AI 模型配置：每个用户独立保存，按需生效（DeepSeek 等 OpenAI 兼容服务）。
 */
export default function ModelSettingsModal({ open, onClose, onSaved }: Props) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; message: string } | null>(null);
  const [hasKey, setHasKey] = useState(false);
  const [mask, setMask] = useState('');

  useEffect(() => {
    if (!open) {
      return;
    }
    setTestResult(null);
    aiApi
      .getConfig()
      .then((cfg) => {
        setHasKey(cfg.hasKey);
        setMask(cfg.apiKeyMask);
        form.setFieldsValue({
          provider: cfg.provider,
          baseUrl: cfg.baseUrl,
          model: cfg.model,
          apiKey: '',
        });
      })
      .catch((e) => message.error(e?.message ?? '读取配置失败'));
  }, [open, form]);

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await aiApi.saveConfig(values);
      message.success('模型配置已保存');
      onSaved?.();
      onClose();
    } catch (e: any) {
      message.error(e?.message ?? '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const test = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const values = await form.validateFields();
      // 先保存当前表单值，再按已保存配置测试
      await aiApi.saveConfig(values);
      const r = await aiApi.testConfig();
      setTestResult({ ok: r.ok, message: r.message });
      onSaved?.();
    } catch (e: any) {
      message.error(e?.message ?? '测试失败');
    } finally {
      setTesting(false);
    }
  };

  const provider = Form.useWatch('provider', form);

  return (
    <Modal
      title="模型配置"
      open={open}
      onCancel={onClose}
      onOk={save}
      okText="保存"
      confirmLoading={saving}
      width={560}
      footer={
        <Space>
          <Button onClick={test} loading={testing}>
            测试连接
          </Button>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={save} loading={saving}>
            保存
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="优先接入你自己的 API Key（如 DeepSeek），配置只对当前账号生效；暂不配置可使用内置示例引擎。"
      />
      <Form form={form} layout="vertical" initialValues={{ provider: 'openai' }}>
        <Form.Item name="provider" label="模型提供方" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'openai', label: 'OpenAI 兼容 API（DeepSeek / 通义 / Kimi 等）— 推荐' },
              { value: 'mock', label: '内置示例引擎（无需配置，仅作体验兜底）' },
            ]}
          />
        </Form.Item>
        {provider === 'openai' && (
          <>
            <Form.Item
              name="baseUrl"
              label="接口地址"
              rules={[{ required: true, message: '请输入接口地址，如 https://api.deepseek.com' }]}
            >
              <Input placeholder="https://api.deepseek.com" />
            </Form.Item>
            <Form.Item name="apiKey" label="API Key">
              <Input.Password
                placeholder={hasKey ? `已配置（${mask}），留空则保持不变` : 'sk-...'}
                autoComplete="off"
              />
            </Form.Item>
            <Form.Item name="model" label="模型名" rules={[{ required: true, message: '请输入模型名，如 deepseek-chat' }]}>
              <Input placeholder="deepseek-chat" />
            </Form.Item>
            {hasKey && (
              <div style={{ marginBottom: 12 }}>
                <Tag color="green">已保存 Key（{mask}）</Tag>
                <span className="muted" style={{ fontSize: 12 }}>
                  重新填写并保存即可更换
                </span>
              </div>
            )}
          </>
        )}
      </Form>
      {testResult && (
        <Alert
          style={{ marginTop: 4 }}
          type={testResult.ok ? 'success' : 'error'}
          showIcon
          message={testResult.message}
        />
      )}
    </Modal>
  );
}
