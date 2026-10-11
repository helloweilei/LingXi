import { useEffect, useState } from 'react';
import { Button, Card, Col, Empty, Modal, Popconfirm, Row, Space, Statistic, Tag, message } from 'antd';
import { BulbOutlined, DeleteOutlined, HistoryOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { novelApi } from '../api';
import type { Novel } from '../api/types';
import NovelFormModal from '../components/NovelFormModal';
import GuideHistoryModal from '../components/GuideHistoryModal';

const STATUS_COLOR: Record<string, string> = {
  草稿: 'default',
  连载中: 'processing',
  已完结: 'success',
};

export default function Dashboard() {
  const navigate = useNavigate();
  const [novels, setNovels] = useState<Novel[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setNovels(await novelApi.list());
    } catch (e: any) {
      message.error(e?.message ?? '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const totalWords = novels.reduce((s, n) => s + (n.wordCount ?? 0), 0);
  const totalChapters = novels.reduce((s, n) => s + (n.chapterCount ?? 0), 0);

  const removeNovel = async (id: number) => {
    try {
      await novelApi.remove(id);
      message.success('作品已删除');
      setNovels((prev) => prev.filter((n) => n.id !== id));
    } catch (e: any) {
      message.error(e?.message ?? '删除失败');
    }
  };

  return (
    <div className="page-container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ margin: 0 }}>我的作品台</h2>
        <Space>
          <Button icon={<HistoryOutlined />} onClick={() => setHistoryOpen(true)}>
            历史方案
          </Button>
          <Button icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            新建作品
          </Button>
          <Button type="primary" icon={<BulbOutlined />} onClick={() => navigate('/guide')}>
            灵感启发
          </Button>
        </Space>
      </div>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card>
            <Statistic title="作品数" value={novels.length} suffix="本" />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="总字数" value={totalWords} suffix="字" />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="总章节" value={totalChapters} suffix="章" />
          </Card>
        </Col>
      </Row>

      {!loading && novels.length === 0 ? (
        <Card>
          <Empty
            description={<span className="muted">还没有作品，从第一本书开始吧</span>}
            style={{ padding: '40px 0' }}
          >
            <Space direction="vertical">
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
                创作第一本书
              </Button>
              <Button icon={<BulbOutlined />} onClick={() => navigate('/guide')}>
                用灵感启发开始
              </Button>
            </Space>
          </Empty>
        </Card>
      ) : (
        <Row gutter={[20, 20]}>
          {novels.map((n) => (
            <Col key={n.id} xs={24} sm={12} lg={8} xl={6}>
              <Card
                hoverable
                loading={loading}
                className="novel-card"
                onClick={() => navigate(`/novel/${n.id}`)}
                cover={
                  <div
                    className="novel-cover"
                    style={{ background: `linear-gradient(140deg, ${n.coverColor || '#6a5acd'}, #2b2b3c)` }}
                  >
                    {n.title}
                  </div>
                }
                actions={[
                  <Popconfirm
                    key="del"
                    title={`确定删除《${n.title}》？`}
                    description="章节与设定将一并删除，不可恢复"
                    okText="删除"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => removeNovel(n.id)}
                  >
                    <span
                      onClick={(e) => e.stopPropagation()}
                      style={{ color: '#9a9ab0' }}
                    >
                      <DeleteOutlined /> 删除
                    </span>
                  </Popconfirm>,
                ]}
              >
                <Card.Meta
                  title={
                    <span>
                      {n.title}{' '}
                      <Tag color={STATUS_COLOR[n.status] ?? 'default'} style={{ marginLeft: 4 }}>
                        {n.status ?? '草稿'}
                      </Tag>
                    </span>
                  }
                  description={
                    <div>
                      <div style={{ marginBottom: 6 }}>
                        {(n.genre ?? '')
                          .split(',')
                          .map((g) => g.trim())
                          .filter(Boolean)
                          .map((g, i) => (
                            <Tag key={i} style={{ marginInlineEnd: 4 }}>
                              {g}
                            </Tag>
                          ))}
                        <span className="muted">
                          {n.chapterCount} 章 · {(n.wordCount ?? 0).toLocaleString()} 字
                        </span>
                      </div>
                      <div className="muted" style={{ fontSize: 12 }}>
                        更新于 {dayjs(n.updatedAt).format('YYYY-MM-DD HH:mm')}
                      </div>
                    </div>
                  }
                />
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <NovelFormModal
        open={modalOpen}
        editing={null}
        onClose={() => setModalOpen(false)}
        onSaved={(n) => {
          message.success('作品已创建');
          setNovels((prev) => [n, ...prev]);
        }}
      />

      <GuideHistoryModal open={historyOpen} onClose={() => setHistoryOpen(false)} />
    </div>
  );
}
