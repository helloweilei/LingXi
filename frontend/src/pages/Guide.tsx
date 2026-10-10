import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Col, Input, Progress, Row, Tag, Typography, message } from 'antd';
import { guideApi } from '../api/guide';
import type { Question, Proposal, GuideState } from '../api/guide';
import GuideChat from '../components/GuideChat';

const { Paragraph, Text } = Typography;

/** 把输入框文本按中文顿号 / 逗号 / 空格拆成选项 token，用于多选与回映 */
function parseTokens(s: string): string[] {
  return s
    .split(/[、,，\s]+/)
    .map((x) => x.trim())
    .filter(Boolean);
}

export default function Guide() {
  const navigate = useNavigate();
  const [intentId, setIntentId] = useState<number | null>(null);
  const [mode, setMode] = useState<'guided' | 'seed' | null>(null);
  const [question, setQuestion] = useState<Question | null>(null);
  const [step, setStep] = useState(0);
  const [total, setTotal] = useState(6);
  const [input, setInput] = useState('');
  const [seedText, setSeedText] = useState('');
  const [generating, setGenerating] = useState(false);
  const [genKey, setGenKey] = useState(0);
  const [proposals, setProposals] = useState<Proposal[]>([]);
  const [busy, setBusy] = useState(false);

  const beginGuided = async () => {
    setBusy(true);
    try {
      const r = await guideApi.start();
      setIntentId(r.intentId);
      setMode(r.mode);
      setQuestion(r.firstQuestion ?? null);
      setTotal(6);
      setStep(0);
      setProposals([]);
    } catch (e: any) {
      message.error(e?.message ?? '启动失败');
    } finally {
      setBusy(false);
    }
  };

  const beginSeed = async () => {
    if (!seedText.trim()) {
      message.warning('请先描述你的模糊想法');
      return;
    }
    setBusy(true);
    try {
      const r = await guideApi.start(seedText.trim());
      setIntentId(r.intentId);
      setMode(r.mode);
      setStep(0);
      setProposals([]);
      setGenerating(true);
      setGenKey((k) => k + 1);
    } catch (e: any) {
      message.error(e?.message ?? '启动失败');
    } finally {
      setBusy(false);
    }
  };

  const submitAnswer = async () => {
    if (!intentId || !question) return;
    const a = input.trim();
    if (!a) {
      message.warning('请先填写再提交');
      return;
    }
    setBusy(true);
    try {
      const r = await guideApi.answer(intentId, a);
      setStep(r.step);
      setTotal(r.total);
      setInput('');
      if (r.done) {
        setQuestion(null);
      } else if (r.question) {
        setQuestion(r.question);
      }
    } catch (e: any) {
      message.error(e?.message ?? '提交失败');
    } finally {
      setBusy(false);
    }
  };

  const afterGen = async () => {
    if (!intentId) return;
    setGenerating(false);
    try {
      const s: GuideState = await guideApi.get(intentId);
      setProposals(s.proposals ?? []);
      setStep(s.answers ? Object.keys(s.answers).filter((k) => k !== 'seed').length : 0);
      setTotal(6);
    } catch (e: any) {
      message.error(e?.message ?? '获取方案失败');
    }
  };

  const selectProposal = async (i: number) => {
    if (!intentId) return;
    setBusy(true);
    try {
      const r = await guideApi.select(intentId, i);
      message.success('作品已生成，进入写作页');
      navigate(`/novel/${r.novelId}`);
    } catch (e: any) {
      message.error(e?.message ?? '生成作品失败');
    } finally {
      setBusy(false);
    }
  };

  const restart = async () => {
    if (intentId) {
      try {
        await guideApi.reset(intentId);
      } catch {
        /* ignore */
      }
    }
    setIntentId(null);
    setMode(null);
    setQuestion(null);
    setProposals([]);
    setInput('');
    setSeedText('');
    setGenerating(false);
    setStep(0);
  };

  return (
    <div className="page-container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ margin: 0 }}>灵感启发 · 从想法到作品</h2>
        {intentId && (
          <Button onClick={restart}>重新开始</Button>
        )}
      </div>

      {!intentId && (
        <Row gutter={20}>
          <Col xs={24} md={12}>
            <Card hoverable onClick={beginGuided} style={{ height: '100%' }}>
              <h3>从零开始（问答引导）</h3>
              <p className="muted">
                还不确定写什么？回答 6 个关于题材、基调、读者、冲突、篇幅、平台的小问题，AI 帮你策划多套原创方案。
              </p>
            </Card>
          </Col>
          <Col xs={24} md={12}>
            <Card style={{ height: '100%' }}>
              <h3>我有个想法（一句话扩展）</h3>
              <Input.TextArea
                rows={3}
                placeholder="例如：想写一个关于「时间循环里的咖啡店」的治愈小故事"
                value={seedText}
                onChange={(e) => setSeedText(e.target.value)}
              />
              <Button type="primary" style={{ marginTop: 12 }} loading={busy} onClick={beginSeed}>
                用这个想法开始
              </Button>
            </Card>
          </Col>
        </Row>
      )}

      {intentId && mode === 'guided' && question && !generating && proposals.length === 0 && (
        <Card>
          <Progress percent={Math.round((step / total) * 100)} showInfo={false} />
          <p className="muted">
            第 {step + 1} / {total} 问 · {question.label}
          </p>
          <Paragraph>{question.prompt}</Paragraph>
          {question.options && question.options.length > 0 && (
            <div style={{ marginBottom: 12 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                快捷选择（可多选，点击切换；也可自行输入）：
              </Text>
              <div style={{ marginTop: 8, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {question.options.map((opt) => (
                  <Tag.CheckableTag
                    key={opt}
                    checked={parseTokens(input).includes(opt)}
                    onChange={() => {
                      const set = new Set(parseTokens(input));
                      if (set.has(opt)) {
                        set.delete(opt);
                      } else {
                        set.add(opt);
                      }
                      setInput([...set].join('、'));
                    }}
                  >
                    {opt}
                  </Tag.CheckableTag>
                ))}
              </div>
            </div>
          )}
          <Input.TextArea
            rows={3}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="写下你的想法，或点击上方快捷选项……"
            onPressEnter={submitAnswer}
          />
          <Button type="primary" style={{ marginTop: 12 }} loading={busy} onClick={submitAnswer}>
            下一步
          </Button>
        </Card>
      )}

      {intentId && ((mode === 'guided' && !question) || mode === 'seed') && !generating && proposals.length === 0 && (
        <Card>
          <p>
            已收集到你的创作偏好{mode === 'seed' && seedText ? `（种子：${seedText}）` : ''}
            ，下面让 AI 生成几套原创方案供你挑选。
          </p>
          <Button type="primary" onClick={() => { setGenerating(true); setGenKey((k) => k + 1); }}>
            生成创意方案
          </Button>
        </Card>
      )}

      {intentId && generating && (
        <Card title="AI 正在策划方案……">
          <GuideChat intentId={intentId} key={genKey} onDone={afterGen} />
        </Card>
      )}

      {!generating && proposals.length > 0 && (
        <>
          <p className="muted">挑一套心仪的，一键生成带大纲、角色与世界观的作品。</p>
          <Row gutter={[20, 20]}>
            {proposals.map((p, i) => (
              <Col xs={24} md={12} xl={8} key={i}>
                <Card
                  title={p.title || `方案 ${i + 1}`}
                  extra={
                    <Button type="primary" size="small" loading={busy} onClick={() => selectProposal(i)}>
                      采用并生成
                    </Button>
                  }
                >
                  {p.platform && (
                    <Tag color="blue" style={{ marginBottom: 8 }}>
                      {p.platform}
                    </Tag>
                  )}
                  <Paragraph type="secondary">{p.premise}</Paragraph>
                  {p.world && (
                    <p>
                      <Text strong>世界观：</Text>
                      {p.world}
                    </p>
                  )}
                  {p.protagonist && (
                    <p>
                      <Text strong>主角：</Text>
                      {p.protagonist}
                    </p>
                  )}
                  {p.characters && p.characters.length > 0 && (
                    <div style={{ marginBottom: 8 }}>
                      <Text strong>主要角色：</Text>{' '}
                      {p.characters.map((c, ci) => (
                        <Tag key={ci}>{c.name}（{c.role || '配角'}）</Tag>
                      ))}
                    </div>
                  )}
                  {p.outline && (
                    <Paragraph style={{ marginTop: 8, whiteSpace: 'pre-wrap' }} ellipsis={{ rows: 4, expandable: true }}>
                      {p.outline}
                    </Paragraph>
                  )}
                </Card>
              </Col>
            ))}
          </Row>
        </>
      )}
    </div>
  );
}
