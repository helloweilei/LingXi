import { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, Modal, Select } from 'antd';
import { novelApi } from '../api';
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

interface Props {
  open: boolean;
  editing: Novel | null;
  onClose: () => void;
  onSaved: (novel: Novel) => void;
}

export default function NovelFormModal({ open, editing, onClose, onSaved }: Props) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      form.setFieldsValue(
        editing
          ? { ...editing, genre: genreToArray(editing.genre) }
          : { title: '', genre: ['玄幻'], description: '', targetWords: 500000, status: '草稿' }
      );
    }
  }, [open, editing, form]);

  const onFinish = async (values: any) => {
    setSaving(true);
    try {
      const payload = { ...values, genre: genreToString(values.genre) };
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

  return (
    <Modal
      title={editing ? '编辑作品信息' : '新建作品'}
      open={open}
      onCancel={onClose}
      footer={null}
      destroyOnClose
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
          <Form.Item name="targetWords" label="目标字数" style={{ flex: 1 }}>
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              step={100000}
              formatter={(v) => `${v}`}
              parser={(v) => Number(v ?? 0) as any}
            />
          </Form.Item>
        </div>
        <Form.Item name="description" label="简介" rules={[{ max: 1000, message: '简介最长 1000 字' }]}>
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
