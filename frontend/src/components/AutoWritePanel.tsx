import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  InputNumber,
  Popconfirm,
  Progress,
  Radio,
  Row,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import { DeleteOutlined, PauseCircleOutlined, PlayCircleOutlined, RobotOutlined } from '@ant-design/icons';
import { taskApi } from '../api';
import type { WritingTaskVO } from '../api/types';

const STATUS_TAG: Record<string, { color: string; text: string }> = {
  QUEUED: { color: 'blue', text: '排队中' },
  RUNNING: { color: 'processing', text: '创作中' },
  WAITING_APPROVAL: { color: 'orange', text: '待确认' },
  DONE: { color: 'green', text: '已完成' },
  FAILED: { color: 'red', text: '失败' },
  CANCELLED: { color: 'default', text: '已取消' },
};

/**
 * 自动创作面板：收集创作意图（模式/章节数/每章字数/大纲/摘要）并发起任务；
 * 轮询展示任务进度；CONFIRM 模式下支持逐章批准继续。
 * 任务进度变化时通过 onProgress 通知父页面刷新（如顶部字数统计）。
 * 默认章节数 6；默认每章字数 = 总字数 / 6（总字数未设置时兜底 1200，区间 300-5000）。
 */
export default function AutoWritePanel({
  novelId,
  targetWords = 0,
  onProgress,
}: {
  novelId: number;
  targetWords?: number;
  onProgress?: () => void;
}) {
  const [form] = Form.useForm();
  const [tasks, setTasks] = useState<WritingTaskVO[]>([]);
  const [starting, setStarting] = useState(false);
  const [resumingId, setResumingId] = useState<number | null>(null);
  const prevSigRef = useRef('');

  const defaultWordsPerChapter =
    targetWords > 0 ? Math.max(300, Math.min(5000, Math.round(targetWords / 6))) : 1200;

  const activeCount = tasks.filter((t) =>
    ['QUEUED', 'RUNNING', 'WAITING_APPROVAL'].includes(t.status)
  ).length;

  const load = useCallback(async () => {
    try {
      const list = await taskApi.list(novelId);
      setTasks(list);
      const sig = list.map((t) => `${t.id}:${t.status}:${t.currentChapter}`).join('|');
      if (sig !== prevSigRef.current) {
        prevSigRef.current = sig;
        onProgress?.();
      }
    } catch (e: any) {
      message.error(e?.message ?? '加载任务失败');
    }
  }, [novelId, onProgress]);

  useEffect(() => {
    load();
  }, [load]);

  // 有进行中任务时每 2.5s 轮询刷新
  useEffect(() => {
    if (activeCount === 0) {
      return;
    }
    const timer = setInterval(load, 2500);
    return () => clearInterval(timer);
  }, [activeCount, load]);

  const start = async () => {
    const values = await form.validateFields();
    setStarting(true);
    try {
      await taskApi.start(novelId, {
        mode: values.mode,
        chapters: values.chapters,
        wordsPerChapter: values.wordsPerChapter,
        genOutline: values.genOutline,
        autoSummary: values.autoSummary,
      });
      message.success('自动创作任务已启动');
      load();
    } catch (e: any) {
      message.error(e?.message ?? '启动失败');
    } finally {
      setStarting(false);
    }
  };

  const approve = async (id: number) => {
    try {
      await taskApi.approve(id);
      message.success('已批准，继续创作');
      load();
    } catch (e: any) {
      message.error(e?.message ?? '操作失败');
    }
  };

  /** 失败任务断点续跑：沿用原任务的参数，从失败处（currentChapter+1）继续生成 */
  const resume = async (t: WritingTaskVO) => {
    setResumingId(t.id);
    try {
      await taskApi.start(t.novelId, {
        mode: t.mode,
        chapters: t.chapters,
        wordsPerChapter: t.wordsPerChapter,
        genOutline: t.genOutline,
        autoSummary: t.autoSummary,
        startChapter: (t.currentChapter ?? 0) + 1,
      });
      message.success('已从第 ' + ((t.currentChapter ?? 0) + 1) + ' 章继续生成');
      load();
    } catch (e: any) {
      message.error(e?.message ?? '续跑失败');
    } finally {
      setResumingId(null);
    }
  };

  const cancel = async (id: number) => {
    try {
      await taskApi.cancel(id);
      message.success('任务已取消');
      load();
    } catch (e: any) {
      message.error(e?.message ?? '取消失败');
    }
  };

  const remove = async (id: number) => {
    try {
      await taskApi.remove(id);
      message.success('任务已删除，生成内容已移除');
      load();
    } catch (e: any) {
      message.error(e?.message ?? '删除失败');
    }
  };

  return (
    <div className="section-card">
      <div style={{ marginBottom: 14 }}>
        <b>AI 自动创作</b>
        <span className="muted" style={{ marginLeft: 10 }}>
          一键流水线：大纲 → 逐章生成 → 摘要落库
        </span>
      </div>
      <Card size="small" style={{ marginBottom: 16, background: '#fafaff' }}>
        <Form
          form={form}
          layout="inline"
          initialValues={{ mode: 'AUTO', chapters: 6, wordsPerChapter: defaultWordsPerChapter, genOutline: true, autoSummary: true }}
          style={{ rowGap: 12 }}
        >
          <Form.Item name="mode" label="模式">
            <Radio.Group
              options={[
                { value: 'AUTO', label: '全自动（一口气写完）' },
                { value: 'CONFIRM', label: '逐章确认（每章写完后我确认再继续）' },
              ]}
              optionType="button"
            />
          </Form.Item>
          <Form.Item name="chapters" label="章节数" rules={[{ required: true }]}>
            <InputNumber min={1} max={50} style={{ width: 90 }} />
          </Form.Item>
          <Form.Item name="wordsPerChapter" label="每章字数" rules={[{ required: true }]}>
            <InputNumber min={300} max={5000} step={100} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item name="genOutline" label="先生成大纲" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="autoSummary" label="生成章节摘要" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item>
            <Button type="primary" icon={<RobotOutlined />} loading={starting} onClick={start}>
              开始创作
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {activeCount > 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="任务进行中，页面每 2.5 秒自动刷新进度，可留在本页等待完成。"
        />
      )}

      <Table
        rowKey="id"
        dataSource={tasks}
        pagination={false}
        size="small"
        locale={{ emptyText: '还没有自动创作任务。设置好章节数等选项，点“开始创作”即可。' }}
        columns={[
          { title: '任务', dataIndex: 'id', width: 70, render: (v: number) => `#${v}` },
          {
            title: '模式',
            dataIndex: 'mode',
            width: 90,
            render: (m: string) => (m === 'AUTO' ? '全自动' : '逐章确认'),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 300,
            render: (s: string, r: WritingTaskVO) => {
              const t = STATUS_TAG[s] ?? { color: 'default', text: s };
              return (
                <Space size={6}>
                  <Tag color={t.color}>{t.text}</Tag>
                  {s === 'FAILED' && r.error && (
                    <span
                      className="muted"
                      style={{
                        fontSize: 12,
                        maxWidth: 210,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        display: 'inline-block',
                        verticalAlign: 'middle',
                      }}
                      title={r.error}
                    >
                      {r.error}
                    </span>
                  )}
                </Space>
              );
            },
          },
          {
            title: '进度',
            dataIndex: 'currentChapter',
            width: 190,
            render: (_: any, r: WritingTaskVO) => {
              const pct = r.totalChapters === 0 ? 0 : Math.round((r.currentChapter / r.totalChapters) * 100);
              return (
                <Space size={8}>
                  <Progress percent={pct} size="small" style={{ width: 90 }} />
                  <span className="muted" style={{ fontSize: 12 }}>
                    {r.currentChapter} / {r.totalChapters} 章
                  </span>
                </Space>
              );
            },
          },
          {
            title: '当前章节',
            dataIndex: 'currentTitle',
            render: (t: string) => t || <span className="muted">—</span>,
          },
          {
            title: '创建时间',
            dataIndex: 'createdAt',
            width: 150,
            render: (t: string) => t?.slice(5, 16) ?? '',
          },
          {
            title: '操作',
            width: 210,
            render: (_: any, r: WritingTaskVO) => (
              <Space>
                {r.status === 'WAITING_APPROVAL' && (
                  <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => approve(r.id)}>
                    批准继续
                  </Button>
                )}
                {['QUEUED', 'RUNNING', 'WAITING_APPROVAL'].includes(r.status) && (
                  <Button type="link" size="small" danger icon={<PauseCircleOutlined />} onClick={() => cancel(r.id)}>
                    取消
                  </Button>
                )}
                {r.status === 'FAILED' && (r.currentChapter ?? 0) < (r.totalChapters ?? 0) && (
                  <Button
                    type="link"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    loading={resumingId === r.id}
                    onClick={() => resume(r)}
                  >
                    继续生成
                  </Button>
                )}
                {!['QUEUED', 'RUNNING', 'WAITING_APPROVAL'].includes(r.status) && (
                  <Popconfirm
                    title="删除任务并移除生成的内容？"
                    description="将删除该任务创建的章节与摘要；若任务生成过全书大纲，大纲也会被清空。章节正文与历史版本会保留（软删除），后续可恢复。"
                    okText="删除"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => remove(r.id)}
                  >
                    <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />
    </div>
  );
}
