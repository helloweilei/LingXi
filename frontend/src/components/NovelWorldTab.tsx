import { useCallback, useEffect, useState } from "react";
import { Button, Card, Col, Empty, Form, Input, Modal, Popconfirm, Row, Spin, message } from "antd";
import { DeleteOutlined, EditOutlined, PlusOutlined } from "@ant-design/icons";
import { worldApi } from "../api";
import type { WorldVO } from "../api/types";

export default function WorldTab({ novelId }: { novelId: number }) {
  const [items, setItems] = useState<WorldVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<WorldVO | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await worldApi.list(novelId));
    } finally {
      setLoading(false);
    }
  }, [novelId]);

  useEffect(() => {
    load();
  }, [load]);

  const openModal = (w: WorldVO | null) => {
    setEditing(w);
    form.setFieldsValue(w ?? { title: "", content: "" });
    setModalOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing) {
      await worldApi.update(editing.id, values);
    } else {
      await worldApi.create(novelId, values);
    }
    message.success("已保存");
    setModalOpen(false);
    load();
  };

  const remove = async (wid: number) => {
    await worldApi.remove(wid);
    message.success("已删除");
    load();
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
        <b>世界观设定</b>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => openModal(null)}
        >
          新增条目
        </Button>
      </div>
      {loading ? (
        <Spin />
      ) : items.length === 0 ? (
        <Empty description="记录力量体系、地理、势力等设定，让 AI 一致地使用它们" />
      ) : (
        <Row gutter={[16, 16]}>
          {items.map((w) => (
            <Col key={w.id} xs={24} sm={12} lg={8}>
              <Card
                size="small"
                title={w.title}
                actions={[
                  <span key="edit" onClick={() => openModal(w)}>
                    <EditOutlined /> 编辑
                  </span>,
                  <Popconfirm
                    key="del"
                    title="确定删除该条目？"
                    okText="删除"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => remove(w.id)}
                  >
                    <span style={{ color: "#9a9ab0" }}>
                      <DeleteOutlined /> 删除
                    </span>
                  </Popconfirm>,
                ]}
              >
                <p style={{ minHeight: 44, whiteSpace: "pre-wrap", margin: 0 }}>
                  {w.content || <span className="muted">（暂无内容）</span>}
                </p>
              </Card>
            </Col>
          ))}
        </Row>
      )}
      <Modal
        title={editing ? "编辑条目" : "新增条目"}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={save}
        okText="保存"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="条目标题"
            rules={[{ required: true, message: "请输入标题" }]}
          >
            <Input
              maxLength={80}
              placeholder="如：力量体系 / 地理格局 / 势力划分"
            />
          </Form.Item>
          <Form.Item name="content" label="内容">
            <Input.TextArea rows={6} maxLength={10000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
