import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type ApiResponse<T> = { success: boolean; data: T; error: { code: string; message: string } | null };
type DatasourceType = 'MYSQL' | 'POSTGRESQL' | 'EXCEL_IMPORT';
type TermType = 'METRIC' | 'DIMENSION' | 'ORG';
type Datasource = { id: number; name: string; type: DatasourceType; host: string; port: number; databaseName: string; username: string; sslEnabled: boolean; enabled: boolean };
type Column = { id: number; columnName: string; dataType: string; nullableCol: boolean; commentText: string; sensitive: boolean; enabled: boolean; ordinalPosition: number };
type Table = { id: number; schemaName: string | null; tableName: string; commentText: string; enabled: boolean; columns: Column[] };
type TermBinding = {
  id?: number;
  datasourceId?: number | null;
  datasource?: Datasource | null;
  tableName: string;
  columnName: string;
  fieldRole: string;
  filterExpression: string;
  valueMappings: string;
  priority: number;
  enabled: boolean;
};
type Term = {
  id: number;
  name: string;
  category: string;
  domain: string;
  termType: TermType;
  synonyms: string;
  aliases: string;
  definitionText: string;
  calculation: string;
  businessRules: string;
  priority: number;
  owner: string;
  verified: boolean;
  enabled: boolean;
  bindings: TermBinding[];
};
type SqlExample = { id: number; question: string; sqlText: string; descriptionText: string; enabled: boolean };
type Session = { id: number; title: string };
type Message = { id: number; role: string; contentText: string; generatedSql?: string };
type Execution = { id: number; question: string; status: string; durationMs?: number; rowCount?: number; errorMessage?: string };
type ExcelImport = { id: number; originalFileName: string; physicalTableName: string; displayTableName: string; rowCount: number; status: string };

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const TERM_TYPES: TermType[] = ['METRIC', 'DIMENSION', 'ORG'];
const TYPE_LABELS: Record<TermType, string> = {
  METRIC: '指标',
  DIMENSION: '维度',
  ORG: '组织',
};

async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('token');
  const isForm = options.body instanceof FormData;
  const headers: Record<string, string> = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers as Record<string, string> | undefined),
  };
  if (!isForm) headers['Content-Type'] = 'application/json';

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (response.status === 401 || response.status === 403) {
    localStorage.removeItem('token');
    window.location.reload();
    throw new Error('登录已失效，请重新登录');
  }
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
          ['terms', '语义资产'],
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
  const [imports, setImports] = useState<ExcelImport[]>([]);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [displayName, setDisplayName] = useState('');
  const [message, setMessage] = useState('');
  const [form, setForm] = useState({
    name: '',
    type: 'MYSQL' as DatasourceType,
    host: 'localhost',
    port: 3306,
    databaseName: '',
    username: '',
    password: '',
    sslEnabled: false,
    enabled: true,
  });

  async function load() {
    setItems(await api<Datasource[]>('/api/datasources'));
    setImports(await api<ExcelImport[]>('/api/excel/imports'));
  }

  useEffect(() => { void load(); }, []);

  async function save() {
    await api('/api/datasources', { method: 'POST', body: JSON.stringify(form) });
    setMessage('数据源已保存');
    await load();
  }

  async function open(ds: Datasource) {
    setSelected(ds);
    setTables(await api<Table[]>(`/api/datasources/${ds.id}/tables`));
  }

  async function sync(ds: Datasource) {
    setTables(await api<Table[]>(`/api/datasources/${ds.id}/sync`, { method: 'POST' }));
    setMessage('表结构已同步');
  }

  async function uploadExcel() {
    if (!uploadFile) {
      setMessage('请选择 .xlsx 文件');
      return;
    }
    const body = new FormData();
    body.append('file', uploadFile);
    if (displayName.trim()) body.append('displayName', displayName.trim());
    const result = await api<ExcelImport>('/api/excel/import', { method: 'POST', body });
    setMessage(`导入成功：${result.displayTableName}，${result.rowCount} 行`);
    setUploadFile(null);
    setDisplayName('');
    await load();
  }

  async function deleteImport(id: number) {
    await api(`/api/excel/imports/${id}`, { method: 'DELETE' });
    setMessage('导入记录已删除');
    await load();
  }

  return (
    <Page title="数据源">
      {message && <p className="notice">{message}</p>}
      <div className="grid two">
        <Panel title="新增数据源">
          <input placeholder="名称" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
          <select value={form.type} onChange={e => {
            const type = e.target.value as DatasourceType;
            setForm({ ...form, type, port: type === 'MYSQL' ? 3306 : 5432 });
          }}>
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
        <Panel title="导入 Excel">
          <input placeholder="显示名称，可选" value={displayName} onChange={e => setDisplayName(e.target.value)} />
          <input type="file" accept=".xlsx" onChange={e => setUploadFile(e.target.files?.[0] || null)} />
          <button onClick={uploadExcel}>上传并导入</button>
          <p className="hint">仅支持 .xlsx，第一个 sheet，首行为表头，最大 20MB / 5 万行。</p>
        </Panel>
      </div>

      <div className="grid two">
        <Panel title="已配置数据源">
          <div className="list">
            {items.map(ds => (
              <div className="row" key={ds.id}>
                <button onClick={() => open(ds)}>{ds.name}</button>
                <span>{ds.type}</span>
                <button onClick={() => sync(ds)}>同步</button>
              </div>
            ))}
          </div>
        </Panel>
        <Panel title="Excel 导入记录">
          <table>
            <thead><tr><th>表名</th><th>文件</th><th>行数</th><th>状态</th><th></th></tr></thead>
            <tbody>
              {imports.map(item => (
                <tr key={item.id}>
                  <td>{item.displayTableName}<br /><small>{item.physicalTableName}</small></td>
                  <td>{item.originalFileName}</td>
                  <td>{item.rowCount}</td>
                  <td>{item.status}</td>
                  <td><button onClick={() => deleteImport(item.id)}>删除</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      </div>

      {selected && <Panel title={`${selected.name} / 表结构`}>
        <div className="schema">
          {tables.map(t => (
            <details key={t.id}>
              <summary>{t.commentText || t.tableName} <small>{t.tableName}</small></summary>
              <table>
                <tbody>
                  {t.columns.map(c => (
                    <tr key={c.id}>
                      <td>{c.columnName}</td>
                      <td>{c.dataType}</td>
                      <td>{c.sensitive ? '敏感' : ''}</td>
                      <td>{c.commentText}</td>
                      <td><small>可在语义资产中绑定</small></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          ))}
        </div>
      </Panel>}
    </Page>
  );
}

function emptyBinding(): TermBinding {
  return { datasourceId: null, tableName: '', columnName: '', fieldRole: '', filterExpression: '', valueMappings: '', priority: 50, enabled: true };
}

function emptyTermForm() {
  return {
    name: '',
    domain: '销售商机',
    category: '销售商机',
    termType: 'DIMENSION' as TermType,
    aliases: '',
    synonyms: '',
    definitionText: '',
    calculation: '',
    businessRules: '',
    priority: 50,
    owner: '',
    verified: false,
    enabled: true,
    bindings: [] as TermBinding[],
  };
}

function TermsPage() {
  const [items, setItems] = useState<Term[]>([]);
  const [datasources, setDatasources] = useState<Datasource[]>([]);
  const [form, setForm] = useState(emptyTermForm());
  const [editingId, setEditingId] = useState<number | null>(null);
  const [message, setMessage] = useState('');
  const [domainFilter, setDomainFilter] = useState('全部');
  const [typeFilter, setTypeFilter] = useState('全部');

  async function load() {
    setItems(await api<Term[]>('/api/terms'));
    setDatasources(await api<Datasource[]>('/api/datasources'));
  }

  useEffect(() => { void load(); }, []);

  const domains = useMemo(() => Array.from(new Set(['销售商机', '通用', ...items.map(item => item.domain || item.category || '销售商机')])), [items]);
  const visibleItems = items.filter(item => {
    const domain = item.domain || item.category || '销售商机';
    return (domainFilter === '全部' || domain === domainFilter) && (typeFilter === '全部' || item.termType === typeFilter);
  });

  async function save() {
    const body = { ...form, category: form.domain, synonyms: form.aliases };
    if (editingId) {
      await api(`/api/terms/${editingId}`, { method: 'PATCH', body: JSON.stringify(body) });
      setMessage('语义资产已更新');
    } else {
      await api('/api/terms', { method: 'POST', body: JSON.stringify(body) });
      setMessage('语义资产已新增');
    }
    reset();
    await load();
  }

  async function seedTemplate() {
    const seeded = await api<Term[]>('/api/terms/templates/sales-opportunity', { method: 'POST' });
    setMessage(`销售商机模板已初始化：${seeded.length} 个术语`);
    await load();
  }

  function edit(term: Term) {
    setEditingId(term.id);
    setForm({
      name: term.name || '',
      domain: term.domain || term.category || '销售商机',
      category: term.domain || term.category || '销售商机',
      termType: term.termType || 'DIMENSION',
      aliases: term.aliases || term.synonyms || '',
      synonyms: term.synonyms || term.aliases || '',
      definitionText: term.definitionText || '',
      calculation: term.calculation || '',
      businessRules: term.businessRules || '',
      priority: term.priority ?? 50,
      owner: term.owner || '',
      verified: Boolean(term.verified),
      enabled: term.enabled,
      bindings: (term.bindings || []).map(binding => ({ ...emptyBinding(), ...binding, datasourceId: binding.datasourceId ?? binding.datasource?.id ?? null })),
    });
  }

  function reset() {
    setEditingId(null);
    setForm(emptyTermForm());
  }

  function updateBinding(index: number, patch: Partial<TermBinding>) {
    setForm(current => ({
      ...current,
      bindings: current.bindings.map((binding, bindingIndex) => bindingIndex === index ? { ...binding, ...patch } : binding),
    }));
  }

  function removeBinding(index: number) {
    setForm(current => ({ ...current, bindings: current.bindings.filter((_, bindingIndex) => bindingIndex !== index) }));
  }

  return <Page title="语义资产管理">
    {message && <p className="notice">{message}</p>}
    <div className="toolbar">
      <button onClick={seedTemplate}>初始化销售商机模板</button>
      <select value={domainFilter} onChange={e => setDomainFilter(e.target.value)}>
        <option value="全部">全部业务域</option>
        {domains.map(domain => <option key={domain} value={domain}>{domain}</option>)}
      </select>
      <select value={typeFilter} onChange={e => setTypeFilter(e.target.value)}>
        <option value="全部">全部类型</option>
        {TERM_TYPES.map(type => <option key={type} value={type}>{TYPE_LABELS[type]}</option>)}
      </select>
    </div>
    <div className="grid two">
      <Panel title={editingId ? '编辑语义资产' : '新增语义资产'}>
        <input placeholder="标准术语，例如：赢单率" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
        <div className="form-row">
          <input placeholder="业务域" value={form.domain} onChange={e => setForm({ ...form, domain: e.target.value, category: e.target.value })} />
          <select value={form.termType} onChange={e => setForm({ ...form, termType: e.target.value as TermType })}>
            {TERM_TYPES.map(type => <option key={type} value={type}>{TYPE_LABELS[type]}</option>)}
          </select>
        </div>
        <input placeholder="ALIAS 别名，用逗号、分号、顿号或空格分隔" value={form.aliases} onChange={e => setForm({ ...form, aliases: e.target.value, synonyms: e.target.value })} />
        <textarea placeholder="业务定义" value={form.definitionText} onChange={e => setForm({ ...form, definitionText: e.target.value })} />
        <textarea placeholder="计算公式，例如：赢单商机数 / 有效商机数" value={form.calculation} onChange={e => setForm({ ...form, calculation: e.target.value })} />
        <textarea placeholder="业务规则，例如：分母排除无效、重复、测试商机" value={form.businessRules} onChange={e => setForm({ ...form, businessRules: e.target.value })} />
        <div className="form-row">
          <input placeholder="负责人" value={form.owner} onChange={e => setForm({ ...form, owner: e.target.value })} />
          <input type="number" placeholder="优先级" value={form.priority} onChange={e => setForm({ ...form, priority: Number(e.target.value) })} />
        </div>
        <div className="check-row">
          <label className="check"><input type="checkbox" checked={form.verified} onChange={e => setForm({ ...form, verified: e.target.checked })} /> 已验证</label>
          <label className="check"><input type="checkbox" checked={form.enabled} onChange={e => setForm({ ...form, enabled: e.target.checked })} /> 启用</label>
        </div>

        <div className="binding-header">
          <h3>标准字段绑定</h3>
          <button onClick={() => setForm({ ...form, bindings: [...form.bindings, emptyBinding()] })}>新增绑定</button>
        </div>
        {form.bindings.map((binding, index) => (
          <div className="binding-card" key={index}>
            <select value={binding.datasourceId || ''} onChange={e => updateBinding(index, { datasourceId: e.target.value ? Number(e.target.value) : null })}>
              <option value="">全部数据源</option>
              {datasources.map(ds => <option key={ds.id} value={ds.id}>{ds.name}</option>)}
            </select>
            <div className="form-row">
              <input placeholder="TABLE 表名" value={binding.tableName} onChange={e => updateBinding(index, { tableName: e.target.value })} />
              <input placeholder="FIELD 字段名" value={binding.columnName} onChange={e => updateBinding(index, { columnName: e.target.value })} />
            </div>
            <input placeholder="字段角色，例如：numerator / denominator / date / status" value={binding.fieldRole} onChange={e => updateBinding(index, { fieldRole: e.target.value })} />
            <textarea placeholder="固定过滤条件，例如：status = '赢单'" value={binding.filterExpression} onChange={e => updateBinding(index, { filterExpression: e.target.value })} />
            <textarea placeholder="VALUE_MAPPING 枚举值映射，例如：赢单=closed_won,成交" value={binding.valueMappings} onChange={e => updateBinding(index, { valueMappings: e.target.value })} />
            <div className="binding-actions">
              <label className="check"><input type="checkbox" checked={binding.enabled} onChange={e => updateBinding(index, { enabled: e.target.checked })} /> 启用</label>
              <button onClick={() => removeBinding(index)}>移除</button>
            </div>
          </div>
        ))}
        <button onClick={save}>{editingId ? '保存修改' : '新增资产'}</button>
        {editingId && <button onClick={reset}>取消编辑</button>}
      </Panel>
      <Panel title="资产列表">
        <table>
          <thead><tr><th>业务域</th><th>类型</th><th>术语</th><th>别名</th><th>绑定</th><th>状态</th><th></th></tr></thead>
          <tbody>
            {visibleItems.map(item => (
              <tr key={item.id}>
                <td>{item.domain || item.category || '销售商机'}</td>
                <td>{TYPE_LABELS[item.termType] || item.termType}</td>
                <td><strong>{item.name}</strong><br /><small>{item.definitionText}</small></td>
                <td>{item.aliases || item.synonyms}</td>
                <td>{(item.bindings || []).length} 个</td>
                <td>{item.enabled ? '启用' : '停用'}{item.verified ? ' / 已验证' : ''}</td>
                <td><button onClick={() => edit(item)}>编辑</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>
    </div>
  </Page>;
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

  return <CrudPage title="SQL 示例" form={<>
    <input placeholder="自然语言问题" value={form.question} onChange={e => setForm({ ...form, question: e.target.value })} />
    <textarea placeholder="SQL" value={form.sqlText} onChange={e => setForm({ ...form, sqlText: e.target.value })} />
    <textarea placeholder="说明" value={form.descriptionText} onChange={e => setForm({ ...form, descriptionText: e.target.value })} />
    <button onClick={save}>保存</button>
  </>} rows={items.map(i => [i.question, i.sqlText, i.descriptionText])} />;
}

function LlmPage() {
  const [form, setForm] = useState({ baseUrl: '', model: '', apiKey: '' });
  const [message, setMessage] = useState('');

  useEffect(() => {
    api<{ baseUrl: string; model: string }>('/api/settings/llm')
      .then(d => setForm({ baseUrl: d.baseUrl, model: d.model, apiKey: '' }));
  }, []);

  async function save() {
    await api('/api/settings/llm', { method: 'PATCH', body: JSON.stringify(form) });
    setMessage('已保存');
  }

  async function test() {
    const res = await api<{ connected: boolean; message: string }>('/api/settings/llm/test', { method: 'POST' });
    setMessage(res.connected ? '连接成功' : res.message);
  }

  return <Page title="模型设置"><Panel title="OpenAI 兼容配置">
    <input placeholder="Base URL" value={form.baseUrl} onChange={e => setForm({ ...form, baseUrl: e.target.value })} />
    <input placeholder="Model" value={form.model} onChange={e => setForm({ ...form, model: e.target.value })} />
    <input placeholder="API Key" type="password" value={form.apiKey} onChange={e => setForm({ ...form, apiKey: e.target.value })} />
    <button onClick={save}>保存</button>
    <button onClick={test}>测试</button>
    <p>{message}</p>
  </Panel></Page>;
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

  useEffect(() => {
    api<Datasource[]>('/api/datasources').then(d => { setDatasources(d); setDatasourceId(d[0]?.id || null); });
    api<Session[]>('/api/chat/sessions').then(setSessions);
  }, []);

  async function ask() {
    setError('');
    try {
      const res = await api('/api/chat/ask', { method: 'POST', body: JSON.stringify({ sessionId, datasourceId, question }) });
      setAnswer(res);
      setQuestion('');
      setSessions(await api<Session[]>('/api/chat/sessions'));
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function openSession(id: number) {
    setSessionId(id);
    setMessages(await api<Message[]>(`/api/chat/sessions/${id}/messages`));
  }

  return <Page title="智能问数">
    <div className="chat-layout">
      <Panel title="会话">{sessions.map(s => <button key={s.id} onClick={() => openSession(s.id)}>{s.title}</button>)}</Panel>
      <Panel title="提问">
        <select value={datasourceId || ''} onChange={e => setDatasourceId(Number(e.target.value))}>
          {datasources.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
        </select>
        <textarea placeholder="例如：本季度按行业统计预计签约金额和赢单率" value={question} onChange={e => setQuestion(e.target.value)} />
        <button onClick={ask}>发送</button>
        {error && <p className="error">{error}</p>}
        {messages.map(m => <div className={`message ${m.role}`} key={m.id}>{m.contentText}{m.generatedSql && <pre>{m.generatedSql}</pre>}</div>)}
        {answer && <Result answer={answer} />}
      </Panel>
    </div>
  </Page>;
}

function Result({ answer }: { answer: any }) {
  return <div className="result">
    <p>{answer.explanation}</p>
    <pre>{answer.sql}</pre>
    <p>{answer.durationMs}ms / {answer.rowCount} rows</p>
    <table>
      <thead><tr>{answer.columns.map((c: string) => <th key={c}>{c}</th>)}</tr></thead>
      <tbody>{answer.rows.map((r: any, idx: number) => <tr key={idx}>{answer.columns.map((c: string) => <td key={c}>{String(r[c] ?? '')}</td>)}</tr>)}</tbody>
    </table>
  </div>;
}

function AuditPage() {
  const [items, setItems] = useState<Execution[]>([]);
  useEffect(() => { api<Execution[]>('/api/query/executions').then(setItems); }, []);
  return <Page title="查询审计"><Panel title="最近 100 条">
    <table>
      <thead><tr><th>ID</th><th>状态</th><th>问题</th><th>行数</th><th>耗时</th><th>错误</th></tr></thead>
      <tbody>{items.map(i => <tr key={i.id}><td>{i.id}</td><td>{i.status}</td><td>{i.question}</td><td>{i.rowCount}</td><td>{i.durationMs}</td><td>{i.errorMessage}</td></tr>)}</tbody>
    </table>
  </Panel></Page>;
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
