import { useCallback, useEffect, useState } from "react";
import type { Key } from "react";
import { Button, Empty, Input, Modal, Popconfirm, Space, Table, message } from "antd";
import { DeleteOutlined, FileAddOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { chapterApi } from "../api";
import type { ChapterMeta } from "../api/types";

export default function ChaptersTab({
  novelId,
  refreshKey,
}: {
  novelId: number;
  refreshKey: number;
}) {
  const navigate = useNavigate();
  const [chapters, setChapters] = useState<ChapterMeta[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [creating, setCreating] = useState(false);
  const [selectedKeys, setSelectedKeys] = useState<Key[]>([]);
  const [batchDeleting, setBatchDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setChapters(await chapterApi.list(novelId));
    } catch (e: any) {
      message.error(e?.message ?? "加载失败");
    } finally {
      setLoading(false);
    }
  }, [novelId]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  const createChapter = async () => {
    setCreating(true);
    try {
      const ch = await chapterApi.create(novelId, {
        title: newTitle || undefined,
      });
      message.success("章节已创建");
      setCreateOpen(false);
      setNewTitle("");
      navigate(`/write/${ch.id}`);
    } catch (e: any) {
      message.error(e?.message ?? "创建失败");
    } finally {
      setCreating(false);
    }
  };

  const removeChapter = async (cid: number) => {
    await chapterApi.remove(cid);
    message.success("章节已删除");
    load();
  };

  const removeBatch = async () => {
    if (selectedKeys.length === 0) {
      return;
    }
    setBatchDeleting(true);
    try {
      await chapterApi.removeBatch(selectedKeys.map(Number));
      message.success(`已删除 ${selectedKeys.length} 章`);
      setSelectedKeys([]);
      load();
    } catch (e: any) {
      message.error(e?.message ?? "批量删除失败");
    } finally {
      setBatchDeleting(false);
    }
  };

  return (
    <div className="section-card">
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          marginBottom: 14,
        }}
      >
        <b>章节列表</b>
        <Space>
          {selectedKeys.length > 0 && (
            <Popconfirm
              title={`删除选中的 ${selectedKeys.length} 章？`}
              description="章节正文与历史版本会保留（软删除），后续可恢复。"
              okText="删除"
              okButtonProps={{ danger: true }}
              onConfirm={removeBatch}
            >
              <Button danger icon={<DeleteOutlined />} loading={batchDeleting}>
                批量删除（{selectedKeys.length}）
              </Button>
            </Popconfirm>
          )}
          <Button
            type="primary"
            icon={<FileAddOutlined />}
            onClick={() => setCreateOpen(true)}
          >
            新建章节
          </Button>
        </Space>
      </div>
      <Table
        rowKey="id"
        loading={loading}
        dataSource={chapters}
        pagination={false}
        rowSelection={{
          selectedRowKeys: selectedKeys,
          onChange: (keys) => setSelectedKeys(keys),
        }}
        locale={{
          emptyText: <Empty description="还没有章节，点击右上角新建" />,
        }}
        columns={[
          { title: "#", dataIndex: "chapterOrder", width: 60 },
          {
            title: "章节名",
            dataIndex: "title",
            render: (t: string, r: ChapterMeta) => (
              <a onClick={() => navigate(`/read/${r.id}`)}>{t}</a>
            ),
          },
          {
            title: "字数",
            dataIndex: "wordCount",
            width: 110,
            render: (w: number) => `${(w ?? 0).toLocaleString()}`,
          },
          {
            title: "更新时间",
            dataIndex: "updatedAt",
            width: 180,
            render: (t: string) => dayjs(t).format("MM-DD HH:mm"),
          },
          {
            title: "操作",
            width: 160,
            render: (_: any, r: ChapterMeta) => (
              <Space>
                <Button
                  type="link"
                  size="small"
                  onClick={() => navigate(`/write/${r.id}`)}
                >
                  编辑
                </Button>
                <Popconfirm
                  title="确定删除这一章？"
                  okText="删除"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => removeChapter(r.id)}
                >
                  <Button type="link" size="small" danger>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title="新建章节"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={createChapter}
        okText="创建并开始写作"
        confirmLoading={creating}
      >
        <Input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="章节名（可留空，默认“未命名章节”）"
          onPressEnter={createChapter}
        />
      </Modal>
    </div>
  );
}
