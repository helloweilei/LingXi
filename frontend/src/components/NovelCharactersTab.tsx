import { useCallback, useEffect, useState } from "react";
import { Button, Card, Col, Empty, Form, Input, Modal, Popconfirm, Row, Select, Space, Spin, Tag, message } from "antd";
import { ApartmentOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from "@ant-design/icons";
import { characterApi } from "../api";
import type { CharacterVO } from "../api/types";
import CharacterGraphModal from "./CharacterGraphModal";

const ROLE_COLOR: Record<string, string> = { 主角: "magenta", 配角: "blue", 反派: "red", 其他: "default" };

export default function CharactersTab({ novelId }: { novelId: number }) {
  const [items, setItems] = useState<CharacterVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<CharacterVO | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [graphOpen, setGraphOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await characterApi.list(novelId));
    } finally {
      setLoading(false);
    }
  }, [novelId]);

  useEffect(() => {
    load();
  }, [load]);

  const openModal = (c: CharacterVO | null) => {
    setEditing(c);
    form.setFieldsValue(c ?? { name: "", role: "配角", description: "" });
    setModalOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing) {
      await characterApi.update(editing.id, values);
    } else {
      await characterApi.create(novelId, values);
    }
    message.success("已保存");
    setModalOpen(false);
    load();
  };

  const remove = async (cid: number) => {
    await characterApi.remove(cid);
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
        <b>角色设定</b>
        <Space>
          <Button icon={<ApartmentOutlined />} onClick={() => setGraphOpen(true)}>
            人物关系图谱
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => openModal(null)}
          >
            新增角色
          </Button>
        </Space>
      </div>
      {loading ? (
        <Spin />
      ) : items.length === 0 ? (
        <Empty description="还没有角色设定。填好角色后，AI 写作会更“懂”他们" />
      ) : (
        <Row gutter={[16, 16]}>
          {items.map((c) => (
            <Col key={c.id} xs={24} sm={12} lg={8}>
              <Card
                size="small"
                title={
                  <span>
                    {c.name}{" "}
                    <Tag color={ROLE_COLOR[c.role] ?? "default"}>{c.role}</Tag>
                  </span>
                }
                actions={[
                  <span key="edit" onClick={() => openModal(c)}>
                    <EditOutlined /> 编辑
                  </span>,
                  <Popconfirm
                    key="del"
                    title="确定删除该角色？"
                    okText="删除"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => remove(c.id)}
                  >
                    <span style={{ color: "#9a9ab0" }}>
                      <DeleteOutlined /> 删除
                    </span>
                  </Popconfirm>,
                ]}
              >
                <p style={{ minHeight: 44, whiteSpace: "pre-wrap", margin: 0 }}>
                  {c.description || <span className="muted">（暂无描述）</span>}
                </p>
              </Card>
            </Col>
          ))}
        </Row>
      )}
      <Modal
        title={editing ? "编辑角色" : "新增角色"}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={save}
        okText="保存"
      >
        <Form form={form} layout="vertical">
          <div style={{ display: "flex", gap: 12 }}>
            <Form.Item
              name="name"
              label="姓名"
              rules={[{ required: true, message: "请输入角色名" }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={60} />
            </Form.Item>
            <Form.Item name="role" label="定位" style={{ flex: 1 }}>
              <Select
                options={["主角", "配角", "反派", "其他"].map((v) => ({
                  value: v,
                  label: v,
                }))}
              />
            </Form.Item>
          </div>
          <Form.Item
            name="description"
            label="人物设定（性格 / 背景 / 能力 / 目标）"
          >
            <Input.TextArea rows={5} maxLength={5000} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <CharacterGraphModal
        open={graphOpen}
        novelId={novelId}
        characters={items}
        onClose={() => setGraphOpen(false)}
      />
    </div>
  );
}
