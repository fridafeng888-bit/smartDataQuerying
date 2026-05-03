import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type ApiResponse<T> = { success: boolean; data: T; error: { code: string; message: string } | null };
type Datasource = { id: number; name: string; type: 'MYSQL' | 'POSTGRESQL'; host: string; port: number; databaseName: string; username: string; sslEnabled: boolean; enabled: boolean };
type Column = { id: number; columnName: string; dataType: string; nullableCol: boolean; commentText: string; sensitive: boolean; enabled: boolean; ordinalPosition: number };
type Table = { id: number; schemaName: string; tableName: string; commentText: string; enabled: boolean; columns: Column[] };
type Term = { id: number; name: string; synonyms: string; definitionText: string; calculation: string; enabled: boolean };
type SqlExample = { id: number; question: string; sqlText: string; descriptionText: string; enabled: boolean };
type Session = { id: number; title: string };
type Message = { id: number; role: string; contentText: string; generatedSql?: string; explanation?: string; errorMessage?: string };
type Execution = { id: number; question: string; generatedSql?: string; status: string; durationMs?: number; rowCount?: number; errorMessage?: string; createdAt: string };

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('token');
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !payload.success) throw new Error(payload.error?.message || 'Request failed');
  return payload.data;
}

function App() {
  const [token, setToken] = useState(localStorage.getItem('token'));
  const [page, setPage] = useState('chat');

  if (!token) return <Login onLogin={setToken} />;

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">smartDataQuerying</div>
        {[
          ['chat', '问数'],
          ['datasources', '数据源'],
          ['terms', '术语库'],
          ['examples', 'SQL 示例'],
          ['llm', '模型设置'],
          ['audit', '审计'],
        ].map(([key, label]) => (
          <button key={key} className={page === key ? 'active' : ''} onClick={() => setPage(key)}>{label}</button>
        ))}
        <button className="ghost" onClick={() => { localStorage.removeItem('token'); setToken(null); }}>退出</button>
      </aside>
      <main>
        {page === 'chat' && <ChatPage />}
        {page === 'datasources' && <DatasourcePage />}
        {page === 'terms' && <TermsPage />}
        {page === 'examples' && <ExamplesPage />}
        {page === 'llm' && <LlmPage />}
        {page === 'audit' && <AuditPage />}
      </main>
    </div>
  );
}

function Login({ onLogin }: { onLogin: (token: string) => void }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin');
  const [error, setError] = useState('');
  async function submit() {
    try {
      const data = await api<{ token: string }>('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) });
      localStorage.setItem('token', data.token);
      onLogin(data.token);
    } catch (err) {
      setError((err as Error).message);
    }
  }
  return (
    <div className="login">
      <section>
        <h1>smartDataQuerying</h1>
        <input value={username} onChange={e => setUsername(e.target.value)} placeholder="用户名" />
        <input value={password} onChange={e => setPassword(e.target.value)} type="password" placeholder="密码" />
        <button onClick={submit}>登录</button>
        {error && <p className="error">{error}</p>}
      </section>
    </div>
  );
}

function DatasourcePage() {
  const [items, setItems] = useState<Datasource[]>([]);
  const [selected, setSelected] = useState<Datasource | null>(null);
  const [tables, setTables] = useState<Table[]>([]);
  const [form, setForm] = useState({ name: '', type: 'MYSQL', host: 'localhost', port: 3306, databaseName: '', username: '', password: '', sslEnabled: false, enabled: true });
  const load = () => api<Datasource[]>('/api/datasources').then(setItems);
  useEffect(() => { void load(); }, []);
  async function save() {
    await api('/api/datasources', { method: 'POST', body: JSON.stringify(form) });
    await load();
  }
  async function open(ds: Datasource) {
    setSelected(ds);
    setTables(await api<Table[]>(`/api/datasources/${ds.id}/tables`));
  }
  async function sync(ds: Datasource) {
    setTables(await api<Table[]>(`/api/datasources/${ds.id}/sync`, { method: 'POST' }));
  }
  return (
    <Page title="数据源">
      <div className="grid two">
        <Panel title="新增数据源">
          <input placeholder="名称" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
          <select value={form.type} onChange={e => setForm({ ...form, type: e.target.value, port: e.target.value === 'MYSQL' ? 3306 : 5432 })}>
            <option value="MYSQL">MySQL</option>
            <option value="POSTGRESQL">PostgreSQL</option>
          </select>
          <input placeholder="Host" value={form.host} onChange={e => setForm({ ...form, host: e.target.value })} />
          <input placeholder="Port" type="number" value={form.port} onChange={e => setForm({ ...form, port: Number(e.target.value) })} />
          <input placeholder="Database" value={form.databaseName} onChange={e => setForm({ ...form, databaseName: e.target.value })} />
          <input placeholder="Username" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} />
          <input placeholder="Password" type="password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} />
          <button onClick={save}>保存</button>
        </Panel>
        <Panel title="已配置">
          <div className="list">
            {items.map(ds => <div className="row" key={ds.id}><button onClick={() => open(ds)}>{ds.name}</button><span>{ds.type}</span><button onClick={() => sync(ds)}>同步</button></div>)}
          </div>
        </Panel>
      </div>
      {selected && <Panel title={`${selected.name} / 表结构`}>
        <div className="schema">{tables.map(t => <details key={t.id}><summary>{t.schemaName}.{t.tableName}</summary><table><tbody>{t.columns.map(c => <tr key={c.id}><td>{c.columnName}</td><td>{c.dataType}</td><td>{c.sensitive ? '敏感' : ''}</td><td>{c.commentText}</td></tr>)}</tbody></table></details>)}</div>
      </Panel>}
    </Page>
  );
}

function TermsPage() {
  const [items, setItems] = useState<Term[]>([]);
  const [form, setForm] = useState({ name: '', synonyms: '', definitionText: '', calculation: '', enabled: true });
  const load = () => api<Term[]>('/api/terms').then(setItems);
  useEffect(() => { void load(); }, []);
  async function save() {
    await api('/api/terms', { method: 'POST', body: JSON.stringify(form) });
    setForm({ name: '', synonyms: '', definitionText: '', calculation: '', enabled: true });
    await load();
  }
  return <CrudPage title="术语库" form={<><input placeholder="术语" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /><input placeholder="同义词" value={form.synonyms} onChange={e => setForm({ ...form, synonyms: e.target.value })} /><textarea placeholder="定义" value={form.definitionText} onChange={e => setForm({ ...form, definitionText: e.target.value })} /><textarea placeholder="计算口径" value={form.calculation} onChange={e => setForm({ ...form, calculation: e.target.value })} /><button onClick={save}>保存</button></>} rows={items.map(i => [i.name, i.synonyms, i.definitionText])} />;
}

function ExamplesPage() {
  const [items, setItems] = useState<SqlExample[]>([]);
  const [form, setForm] = useState({ question: '', sqlText: '', descriptionText: '', enabled: true });
  const load = () => api<SqlExample[]>('/api/sql-examples').then(setItems);
  useEffect(() => { void load(); }, []);
  async function save() {
    await api('/api/sql-examples', { method: 'POST', body: JSON.stringify(form) });
    setForm({ question: '', sqlText: '', descriptionText: '', enabled: true });
    await load();
  }
  return <CrudPage title="SQL 示例" form={<><input placeholder="自然语言问题" value={form.question} onChange={e => setForm({ ...form, question: e.target.value })} /><textarea placeholder="SQL" value={form.sqlText} onChange={e => setForm({ ...form, sqlText: e.target.value })} /><textarea placeholder="说明" value={form.descriptionText} onChange={e => setForm({ ...form, descriptionText: e.target.value })} /><button onClick={save}>保存</button></>} rows={items.map(i => [i.question, i.sqlText, i.descriptionText])} />;
}

function LlmPage() {
  const [form, setForm] = useState({ baseUrl: '', model: '', apiKey: '' });
  const [message, setMessage] = useState('');
  useEffect(() => { api<{ baseUrl: string; model: string }>('/api/settings/llm').then(d => setForm({ baseUrl: d.baseUrl, model: d.model, apiKey: '' })); }, []);
  async function save() {
    await api('/api/settings/llm', { method: 'PATCH', body: JSON.stringify(form) });
    setMessage('已保存');
  }
  async function test() {
    const res = await api<{ connected: boolean; message: string }>('/api/settings/llm/test', { method: 'POST' });
    setMessage(res.connected ? '连接成功' : res.message);
  }
  return <Page title="模型设置"><Panel title="OpenAI 兼容配置"><input placeholder="Base URL" value={form.baseUrl} onChange={e => setForm({ ...form, baseUrl: e.target.value })} /><input placeholder="Model" value={form.model} onChange={e => setForm({ ...form, model: e.target.value })} /><input placeholder="API Key" type="password" value={form.apiKey} onChange={e => setForm({ ...form, apiKey: e.target.value })} /><button onClick={save}>保存</button><button onClick={test}>测试</button><p>{message}</p></Panel></Page>;
}

function ChatPage() {
  const [datasources, setDatasources] = useState<Datasource[]>([]);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [messages, setMessages] = useState<Message[]>([]);
  const [datasourceId, setDatasourceId] = useState<number | null>(null);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState<any>(null);
  const [error, setError] = useState('');
  useEffect(() => { api<Datasource[]>('/api/datasources').then(d => { setDatasources(d); setDatasourceId(d[0]?.id || null); }); api<Session[]>('/api/chat/sessions').then(setSessions); }, []);
  async function ask() {
    setError('');
    try {
      const res = await api('/api/chat/ask', { method: 'POST', body: JSON.stringify({ sessionId, datasourceId, question }) });
      setAnswer(res);
      setQuestion('');
      setSessions(await api<Session[]>('/api/chat/sessions'));
    } catch (err) { setError((err as Error).message); }
  }
  async function openSession(id: number) {
    setSessionId(id);
    setMessages(await api<Message[]>(`/api/chat/sessions/${id}/messages`));
  }
  return <Page title="智能问数"><div className="chat-layout"><Panel title="会话">{sessions.map(s => <button key={s.id} onClick={() => openSession(s.id)}>{s.title}</button>)}</Panel><Panel title="提问"><select value={datasourceId || ''} onChange={e => setDatasourceId(Number(e.target.value))}>{datasources.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select><textarea placeholder="例如：统计最近 30 天每天的订单数" value={question} onChange={e => setQuestion(e.target.value)} /><button onClick={ask}>发送</button>{error && <p className="error">{error}</p>}{messages.map(m => <div className={`message ${m.role}`} key={m.id}>{m.contentText}{m.generatedSql && <pre>{m.generatedSql}</pre>}</div>)}{answer && <Result answer={answer} />}</Panel></div></Page>;
}

function Result({ answer }: { answer: any }) {
  return <div className="result"><p>{answer.explanation}</p><pre>{answer.sql}</pre><p>{answer.durationMs}ms / {answer.rowCount} rows</p><table><thead><tr>{answer.columns.map((c: string) => <th key={c}>{c}</th>)}</tr></thead><tbody>{answer.rows.map((r: any, idx: number) => <tr key={idx}>{answer.columns.map((c: string) => <td key={c}>{String(r[c] ?? '')}</td>)}</tr>)}</tbody></table></div>;
}

function AuditPage() {
  const [items, setItems] = useState<Execution[]>([]);
  useEffect(() => { api<Execution[]>('/api/query/executions').then(setItems); }, []);
  return <Page title="查询审计"><Panel title="最近 100 条"><table><thead><tr><th>ID</th><th>状态</th><th>问题</th><th>行数</th><th>耗时</th><th>错误</th></tr></thead><tbody>{items.map(i => <tr key={i.id}><td>{i.id}</td><td>{i.status}</td><td>{i.question}</td><td>{i.rowCount}</td><td>{i.durationMs}</td><td>{i.errorMessage}</td></tr>)}</tbody></table></Panel></Page>;
}

function CrudPage({ title, form, rows }: { title: string; form: React.ReactNode; rows: string[][] }) {
  return <Page title={title}><div className="grid two"><Panel title="新增">{form}</Panel><Panel title="列表"><table><tbody>{rows.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j}>{c}</td>)}</tr>)}</tbody></table></Panel></div></Page>;
}

function Page({ title, children }: { title: string; children: React.ReactNode }) {
  return <><header><h1>{title}</h1></header>{children}</>;
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return <section className="panel"><h2>{title}</h2>{children}</section>;
}

createRoot(document.getElementById('root')!).render(<App />);
