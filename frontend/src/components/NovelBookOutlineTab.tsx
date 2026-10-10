import { Button, Space } from "antd";
import { RobotOutlined, SaveOutlined } from "@ant-design/icons";
import MDEditor from "@uiw/react-md-editor";

type NovelBookOutlineTabProps = { outline: string; onChange: (value: string) => void; onSave: () => void; onGenerate: () => void };

export default function NovelBookOutlineTab({ outline, onChange, onSave, onGenerate }: NovelBookOutlineTabProps) {
  return (
    <div className="section-card">
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
        <Space><b>全书大纲</b><span className="muted">AI 生成时会结合书名、简介与角色设定</span></Space>
        <Space>
          <Button icon={<RobotOutlined />} type="primary" ghost onClick={onGenerate}>AI 生成大纲</Button>
          <Button icon={<SaveOutlined />} onClick={onSave}>保存大纲</Button>
        </Space>
      </div>
      <div data-color-mode="light">
        <MDEditor value={outline} onChange={(value) => onChange(value ?? "")} height="auto" preview="edit" visibleDragbar={false} textareaProps={{ placeholder: "在这里写下或让 AI 生成全书大纲：分卷、核心冲突、关键章节梗概……" }} />
      </div>
    </div>
  );
}
