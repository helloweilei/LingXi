import { useEffect, useRef, useState } from 'react';
import { Button, Form, Input, InputNumber, Modal, Select, Space, message } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { novelApi } from '../api';
import { streamSse } from '../api/ai';
import type { Novel } from '../api/types';

export const GENRES = [
  {
    label: '男频',
    options: [
      '玄幻', '仙侠', '奇幻', '武侠', '都市', '军事', '历史', '游戏', '体育', '科幻', '悬疑', '惊悚', '灵异',
    ].map((g) => ({ value: g, label: g })),
  },
  {
    label: '女频',
    options: [
      '现代言情', '古代言情', '青春校园', '幻想言情', '悬疑言情', '种田文', '宫斗宅斗', '重生', '穿越',
    ].map((g) => ({ value: g, label: g })),
  },
  {
    label: '通用',
    options: [
      '轻小说', '同人', '短篇', '现实', '脑洞', '其他',
    ].map((g) => ({ value: g, label: g })),
  },
];

/** 把库里的逗号分隔类型串转成数组；数组 join 回存储串 */
export const genreToArray = (v?: string | null) => (v ?? '').split(',').map((s) => s.trim()).filter(Boolean);
export const genreToString = (arr?: string[]) => (arr ?? []).join(',');
export const NOVEL_STATUS = ['草稿', '连载中', '已完结'];

/** 预置语言风格（支持自定义输入） */
export const LANGUAGE_STYLES = [
  '现代白话',
  '古典文风',
  '轻松幽默',
  '热血激昂',
  '文艺唯美',
  '悬疑冷峻',
  '简洁干练',
  '口语化',
  '华丽辞藻',
  '画面感强',
];

/** 语言风格多选以「、」连接存储，AI 提示词可直接读作自然中文 */
const STYLE_SEPARATOR = '、';
export const styleToArray = (v?: string | null) =>
  (v ?? '').split(STYLE_SEPARATOR).map((s) => s.trim()).filter(Boolean);
export const styleToString = (v?: string[] | string | null) =>
  (Array.isArray(v) ? v : styleToArray(v)).map((s) => s.trim()).filter(Boolean).join(STYLE_SEPARATOR);

interface Props {
  open: boolean;
  editing: Novel | null;
  onClose: () => void;
  onSaved: (novel: Novel) => void;
  /** 全书大纲：有内容时简介栏提供「根据大纲一键生成」（编辑场景由详情页传入） */
  outline?: string | null;
}

export default function NovelFormModal({ open, editing, onClose, onSaved, outline }: Props) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  /** 正在根据大纲流式生成简介 */
  const [genDesc, setGenDesc] = useState(false);
  const genAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (open) {
      form.setFieldsValue(
        editing
          ? { ...editing, genre: genreToArray(editing.genre), languageStyle: styleToArray(editing.languageStyle) }
          : { title: '', genre: ['玄幻'], description: '', targetWords: 500000, status: '草稿', languageStyle: [] }
      );
    }
  }, [open, editing, form]);

  // 弹窗关闭/卸载时中断未完成的简介生成，避免回写已销毁的表单
  useEffect(() => {
    if (!open) {
      genAbortRef.current?.abort();
    }
  }, [open]);

  const onFinish = async (values: any) => {
    setSaving(true);
    try {
      const payload = {
        ...values,
        genre: genreToString(values.genre),
        languageStyle: styleToString(values.languageStyle),
      };
      const saved = editing
        ? await novelApi.update(editing.id, payload)
        : await novelApi.create(payload);
      onSaved(saved);
      onClose();
    } catch (e: any) {
      Modal.error({ title: '保存失败', content: e?.message ?? '' });
    } finally {
      setSaving(false);
    }
  };

  /** 依据全书大纲一键生成简介：流式逐字填充简介框，可直接改后再保存 */
  const genDescription = () => {
    if (!editing || genDesc) return;
    const current = form.getFieldValue('description') as string | undefined;
    const run = () => {
      setGenDesc(true);
      const ctrl = new AbortController();
      genAbortRef.current = ctrl;
      let acc = '';
      streamSse(
        `/api/ai/novels/${editing.id}/description`,
        null,
        {
          onDelta: (d) => {
            acc += d;
            form.setFieldValue('description', acc);
          },
        },
        ctrl.signal,
      )
        .then(() => message.success('简介已生成，可继续编辑后保存'))
        .catch((e: any) => {
          if (e?.name !== 'AbortError') {
            message.error(e?.message ?? '生成失败，请稍后重试');
          }
        })
        .finally(() => {
          setGenDesc(false);
          genAbortRef.current = null;
        });
    };
    if (current?.trim()) {
      Modal.confirm({
        title: '根据大纲重新生成简介？',
        content: '当前简介会被新生成的内容覆盖。',
        okText: '生成',
        cancelText: '取消',
        onOk: run,
      });
    } else {
      run();
    }
  };

  return (
    <Modal
      title={editing ? '编辑作品信息' : '新建作品'}
      open={open}
      onCancel={onClose}
      footer={null}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" onFinish={onFinish} style={{ marginTop: 12 }}>
        <Form.Item
          name="title"
          label="书名"
          rules={[{ required: true, message: '请输入书名' }, { max: 80, message: '书名最长 80 字' }]}
        >
          <Input placeholder="例如：星降大陆" maxLength={80} showCount />
        </Form.Item>
        <div style={{ display: 'flex', gap: 12 }}>
          <Form.Item name="genre" label="类型" style={{ flex: 1 }} rules={[{ required: true, message: '请至少选择一个类型' }]}>
            <Select
              mode="multiple"
              allowClear
              placeholder="可多选，最多 6 个"
              maxTagCount={3}
              options={GENRES}
              optionFilterProp="label"
              onChange={(v: string[]) => (v.length > 6 ? form.setFieldValue('genre', v.slice(0, 6)) : undefined)}
            />
          </Form.Item>
          {editing && (
            <Form.Item name="status" label="状态" style={{ flex: 1 }}>
              <Select options={NOVEL_STATUS.map((s) => ({ value: s, label: s }))} />
            </Form.Item>
          )}
        </div>
        <Form.Item name="targetWords" label="目标字数">
          <InputNumber
            style={{ width: '100%' }}
            min={0}
            step={100000}
            formatter={(v) => `${v}`}
            parser={(v) => Number(v ?? 0) as any}
          />
        </Form.Item>
        <Form.Item
          name="languageStyle"
          label="语言风格"
          tooltip="可多选，也可直接输入自定义文风描述（不限字数），输入后按回车添加"
        >
          <Select
            mode="tags"
            allowClear
            placeholder="选择或输入，如：轻松幽默、多用短句少用形容词"
            options={LANGUAGE_STYLES.map((s) => ({ value: s, label: s }))}
            tokenSeparators={['、']}
          />
        </Form.Item>
        <Form.Item
          name="description"
          label={
            <Space size={8}>
              <span>简介</span>
              {editing && outline?.trim() && (
                <Button
                  size="small"
                  type="link"
                  icon={<ThunderboltOutlined />}
                  loading={genDesc}
                  onClick={genDescription}
                >
                  {genDesc ? '生成中…' : '根据大纲一键生成'}
                </Button>
              )}
            </Space>
          }
          rules={[{ max: 1000, message: '简介最长 1000 字' }]}
        >
          <Input.TextArea
            rows={4}
            maxLength={1000}
            showCount
            placeholder="一两句话介绍你的故事，AI 会据此理解整本书的定位"
          />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={saving}>
          {editing ? '保存修改' : '创建作品'}
        </Button>
      </Form>
    </Modal>
  );
}
