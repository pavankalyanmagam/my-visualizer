import { useEffect, useMemo, useRef, useState } from 'react'
import Prism from 'prismjs'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-java'
import 'prismjs/themes/prism-tomorrow.css'
import './app.css'
import type { HeapObject, TraceFile, TraceStep } from './trace'
import baseTrace from './traces/moveZeroes.json'

type InputCase = TraceFile['inputs'][number]

type TraceBundle = TraceFile & {
  inputs: InputCase[]
}

const SPEEDS = [0.5, 1, 1.5, 2]

const makeArrayHeap = (name: string, ref: string, items: number[]): HeapObject => ({
  kind: 'array',
  ref,
  name,
  items
})

const makeListHeap = (name: string, head: string): HeapObject => ({
  kind: 'list',
  ref: `${name}-list`,
  name,
  head
})

const makeNode = (ref: string, value: number, next?: string): HeapObject => ({
  kind: 'node',
  ref,
  value,
  next
})

const makeTreeNode = (ref: string, value: number, left?: string, right?: string): HeapObject => ({
  kind: 'node',
  ref,
  value,
  left,
  right
})

const makeGraph = (): HeapObject => ({
  kind: 'graph',
  ref: 'graph-1',
  name: 'graph',
  nodes: [
    { id: 'A', label: 'A' },
    { id: 'B', label: 'B' },
    { id: 'C', label: 'C' },
    { id: 'D', label: 'D' }
  ],
  edges: [
    { from: 'A', to: 'B' },
    { from: 'A', to: 'C' },
    { from: 'B', to: 'D' },
    { from: 'C', to: 'D' }
  ]
})

const buildMoveZeroesTrace = (numsInput: number[]): TraceStep[] => {
  const trace: TraceStep[] = []
  let nums = [...numsInput]
  let writePos = 0
  for (let readPos = 0; readPos < nums.length; readPos++) {
    trace.push({
      line: 3,
      locals: { readPos, writePos },
      heap: [makeArrayHeap('nums', 'arr-1', [...nums])],
      focus: { array: 'arr-1', indices: { readPos, writePos } }
    })
    if (nums[readPos] !== 0) {
      trace.push({
        line: 4,
        locals: { readPos, writePos },
        heap: [makeArrayHeap('nums', 'arr-1', [...nums])],
        focus: { array: 'arr-1', indices: { readPos, writePos } }
      })
      const temp = nums[writePos]
      nums[writePos] = nums[readPos]
      nums[readPos] = temp
      trace.push({
        line: 6,
        locals: { readPos, writePos },
        heap: [makeArrayHeap('nums', 'arr-1', [...nums])],
        focus: { array: 'arr-1', indices: { readPos, writePos } }
      })
      writePos++
      trace.push({
        line: 8,
        locals: { readPos, writePos },
        heap: [makeArrayHeap('nums', 'arr-1', [...nums])],
        focus: {
          array: 'arr-1',
          indices: { readPos, writePos: Math.min(writePos, nums.length - 1) }
        }
      })
    }
  }
  trace.push({
    line: 10,
    locals: { readPos: nums.length, writePos },
    heap: [makeArrayHeap('nums', 'arr-1', [...nums])]
  })
  return trace
}

const hydrateTrace = (): TraceBundle => {
  const bundle = baseTrace as TraceFile
  const inputs = bundle.inputs.map((input) => {
    const numbers = input.value
      .split('[')[1]
      ?.split(']')[0]
      ?.split(',')
      .map((value) => Number(value.trim()))
      .filter((value) => !Number.isNaN(value)) ?? []

    return {
      ...input,
      trace: buildMoveZeroesTrace(numbers)
    }
  })

  return {
    ...bundle,
    inputs
  }
}

function useHighlightedCode(code: string, language: string) {
  return useMemo(() => {
    const grammar = language === 'Python' ? Prism.languages.python : Prism.languages.java
    return Prism.highlight(code, grammar, language.toLowerCase())
  }, [code, language])
}

function CodeEditor({
  code,
  language,
  activeLine,
  onChange
}: {
  code: string
  language: string
  activeLine?: number
  onChange: (value: string) => void
}) {
  const highlighted = useHighlightedCode(code, language)
  const textAreaRef = useRef<HTMLTextAreaElement | null>(null)
  const wrapperRef = useRef<HTMLDivElement | null>(null)

  const handleScroll = (event: React.UIEvent<HTMLTextAreaElement>) => {
    const textarea = event.currentTarget
    const overlay = textarea.previousElementSibling as HTMLPreElement | null
    const lines = wrapperRef.current?.previousElementSibling as HTMLDivElement | null
    
    if (overlay) {
      overlay.scrollTop = textarea.scrollTop
      overlay.scrollLeft = textarea.scrollLeft
    }
    
    if (lines) {
      lines.scrollTop = textarea.scrollTop
    }
  }

  return (
    <div className="editor">
      <div className="editor-lines">
        {code.split('\n').map((_, i) => (
          <div key={i} className={`line-number ${activeLine === i + 1 ? 'active' : ''}`}>
            {i + 1}
          </div>
        ))}
      </div>
      <div className="editor-wrapper" ref={wrapperRef}>
        <pre className={`editor-highlight language-${language.toLowerCase()}`}>
          <code dangerouslySetInnerHTML={{ __html: highlighted }} />
        </pre>
        {activeLine && (
          <div
            className="editor-active-line"
            style={{ top: `${(activeLine - 1) * 24 + 20}px` }}
          />
        )}
        <textarea
          ref={textAreaRef}
          className="editor-input"
          value={code}
          onChange={(event) => onChange(event.target.value)}
          onScroll={handleScroll}
          spellCheck={false}
        />
      </div>
    </div>
  )
}

function ArrayView({ array, focus, locals }: { array: HeapObject; focus?: TraceStep['focus']; locals?: TraceStep['locals'] }) {
  if (array.kind !== 'array') return null
  const indices = focus?.array === array.ref ? focus.indices : undefined
  
  // Find pointers (number locals that point to this index)
  const pointers: Record<number, string[]> = {}
  if (locals) {
    Object.entries(locals).forEach(([name, value]) => {
      if (typeof value === 'number' && Number.isInteger(value) && value >= 0 && value < array.items.length) {
        // Exclude common non-pointer variable names if necessary, but generally show all valid indices
        if (!pointers[value]) pointers[value] = []
        pointers[value].push(name)
      }
    })
  }

  return (
    <div className="array">
      {array.items.map((value, index) => {
        const readPos = indices?.readPos
        const writePos = indices?.writePos
        const isRead = readPos === index
        const isWrite = writePos === index
        const localPointers = pointers[index] || []
        
        const highlightClass = isRead && isWrite ? 'focus dual' : isRead ? 'focus read' : isWrite ? 'focus write' : localPointers.length > 0 ? 'focus' : ''
        
        return (
          <div key={index} className={`cell ${highlightClass}`}>
            <span>{value}</span>
            {isRead && <div className="marker top">readPos</div>}
            {isWrite && <div className="marker bottom">writePos</div>}
            {localPointers.length > 0 && (
              <div className="marker bottom" style={{ top: '64px', bottom: 'auto', lineHeight: '14px' }}>
                {localPointers.join(', ')}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

function LinkedListView({ list, nodes, focus }: { list: HeapObject; nodes: HeapObject[]; focus?: TraceStep['focus'] }) {
  if (list.kind !== 'list') return null
  const nodeMap = new Map(nodes.filter((node) => node.kind === 'node').map((node) => [node.ref, node]))
  const order: HeapObject[] = []
  let cursor = list.head
  while (cursor && nodeMap.has(cursor) && order.length < 10) {
    const node = nodeMap.get(cursor)!
    order.push(node)
    cursor = (node as any).next
  }

  return (
    <div className="list">
      {order.map((node, index) => (
        <div key={node.ref} className={`list-node ${focus?.refs?.includes(node.ref) ? 'focus' : ''}`}>
          <span>{node.value}</span>
          {index < order.length - 1 && <div className="list-arrow">→</div>}
        </div>
      ))}
    </div>
  )
}

function TreeView({ rootRef, nodes, focus }: { rootRef: string; nodes: HeapObject[]; focus?: TraceStep['focus'] }) {
  const nodeMap = new Map(nodes.filter((node) => node.kind === 'node').map((node) => [node.ref, node]))
  const levels: HeapObject[][] = []
  let current: string[] = [rootRef]
  for (let depth = 0; depth < 3; depth++) {
    const levelNodes = current.map((ref) => nodeMap.get(ref)).filter(Boolean) as HeapObject[]
    if (levelNodes.length === 0) break
    levels.push(levelNodes)
    current = levelNodes.flatMap((node) => [(node as any).left, (node as any).right].filter(Boolean)) as string[]
  }

  return (
    <div className="tree">
      {levels.map((level, index) => (
        <div key={index} className="tree-level">
          {level.map((node) => (
            <div key={node.ref} className={`tree-node ${focus?.refs?.includes(node.ref) ? 'focus' : ''}`}>
              {node.value}
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

function GraphView({ graph, focus }: { graph: HeapObject; focus?: TraceStep['focus'] }) {
  if (graph.kind !== 'graph') return null
  const size = 180
  const radius = 70
  const center = size / 2
  const points = graph.nodes.map((node, index) => {
    const angle = (2 * Math.PI * index) / graph.nodes.length
    return {
      ...node,
      x: center + radius * Math.cos(angle),
      y: center + radius * Math.sin(angle)
    }
  })

  const findPoint = (id: string) => points.find((point) => point.id === id)!

  return (
    <div className="graph">
      <svg width={size} height={size}>
        {graph.edges.map((edge, index) => {
          const from = findPoint(edge.from)
          const to = findPoint(edge.to)
          return <line key={index} x1={from.x} y1={from.y} x2={to.x} y2={to.y} stroke="#4a5165" strokeWidth={2} />
        })}
        {points.map((node) => (
          <g key={node.id}>
            <circle
              cx={node.x}
              cy={node.y}
              r={16}
              fill={focus?.refs?.includes(node.id) ? '#fbd15f' : '#23b0e1'}
              stroke="#0a0b0d"
              strokeWidth={2}
            />
            <text x={node.x} y={node.y + 4} textAnchor="middle" fontSize={11} fill="#0b0c10" fontWeight={700}>
              {node.label}
            </text>
          </g>
        ))}
      </svg>
    </div>
  )
}

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export default function App() {
  const traceBundle = useMemo(() => hydrateTrace(), [])
  const [language, setLanguage] = useState(traceBundle.language)
  const [code, setCode] = useState(traceBundle.code)
  const [activeInput, setActiveInput] = useState(traceBundle.inputs[0].id)
  const [stepIndex, setStepIndex] = useState(0)
  const [isPlaying, setIsPlaying] = useState(false)
  const [speed, setSpeed] = useState(1)
  const [traceData, setTraceData] = useState<TraceBundle>(traceBundle)
  const [draftInputs, setDraftInputs] = useState(() =>
    traceBundle.inputs.map((input) => ({
      id: input.id,
      label: input.label,
      value: input.value
    }))
  )
  const [isRunning, setIsRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const currentInput = useMemo(
    () => traceData.inputs.find((input) => input.id === activeInput) ?? traceData.inputs[0],
    [activeInput, traceData.inputs]
  )

  const trace = currentInput.trace
  const currentStep = trace[stepIndex]

  useEffect(() => {
    setStepIndex(0)
    setIsPlaying(false)
  }, [activeInput])

  useEffect(() => {
    setDraftInputs(
      traceData.inputs.map((input) => ({
        id: input.id,
        label: input.label,
        value: input.value
      }))
    )
  }, [traceData])

  useEffect(() => {
    if (!isPlaying) return
    if (stepIndex >= trace.length - 1) {
      setIsPlaying(false)
      return
    }
    const id = window.setTimeout(() => {
      setStepIndex((prev) => Math.min(prev + 1, trace.length - 1))
    }, 900 / speed)
    return () => window.clearTimeout(id)
  }, [isPlaying, stepIndex, trace.length, speed])

  const arrayHeap = currentStep?.heap.find((item) => item.kind === 'array')
  const listHeap = currentStep?.heap.find((item) => item.kind === 'list')
  const graphHeap = currentStep?.heap.find((item) => item.kind === 'graph')
  const treeRoot = currentStep?.heap.find((item) => item.kind === 'node' && item.ref === 'tree-1')

  const activeDraft = draftInputs.find((input) => input.id === activeInput) ?? draftInputs[0]

  const handleInputChange = (value: string) => {
    setDraftInputs((prev) =>
      prev.map((input) => (input.id === activeInput ? { ...input, value } : input))
    )
  }

  const handleFileLoad = async (file?: File | null) => {
    if (!file) return
    const text = await file.text()
    const parsed = JSON.parse(text) as TraceFile
    const hydrated: TraceBundle = {
      ...parsed,
      inputs: parsed.inputs.map((input) => ({
        ...input,
        trace: input.trace
      }))
    }
    setTraceData(hydrated)
    setLanguage(hydrated.language)
    setCode(hydrated.code)
    setActiveInput(hydrated.inputs[0].id)
  }

  const runTrace = async () => {
    setIsRunning(true)
    setError(null)
    try {
      const response = await fetch(`${API_URL}/run`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: traceData.title,
          language,
          code,
          inputs: draftInputs
        })
      })
      const payload = await response.json()
      if (!response.ok) {
        throw new Error(payload.error || 'Failed to run trace')
      }
      setTraceData(payload)
      setActiveInput(payload.inputs[0]?.id ?? activeInput)
      setStepIndex(0)
      setIsPlaying(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to run trace')
    } finally {
      setIsRunning(false)
    }
  }

  return (
    <div className="app">
      <header className="topbar">
        <div>
          <h1>{traceData.title}</h1>
          <p className="subtitle">Execution Visualizer</p>
        </div>
        <div className="top-actions">
          <button className="run-button" onClick={runTrace} disabled={isRunning}>
            {isRunning ? 'Running...' : 'Run'}
          </button>
          <label className="file-upload">
            Load Trace
            <input type="file" accept="application/json" onChange={(event) => handleFileLoad(event.target.files?.[0])} />
          </label>
          <button className="bookmark">Bookmark</button>
        </div>
      </header>
      {error && <div className="error-banner">Run failed: {error}</div>}

      <div className="content">
        <section className="panel code-panel">
          <div className="panel-header">
            <div className="select">
              <select value={language} onChange={(event) => setLanguage(event.target.value)}>
                <option>Java</option>
                <option>Python</option>
              </select>
            </div>
            <div className="panel-actions">
              <button className="icon-button">#</button>
              <button className="icon-button">⟲</button>
              <button className="icon-button">⧉</button>
            </div>
          </div>
          <CodeEditor code={code} language={language} activeLine={currentStep?.line} onChange={setCode} />
        </section>

        <section className="panel right-panel">
          <div className="panel-header">
            <div className="panel-title">Input</div>
            <button className="icon-button">⌄</button>
          </div>
          <div className="input-tabs">
            {traceData.inputs.map((input) => (
              <button
                key={input.id}
                className={`tab ${input.id === activeInput ? 'active' : ''}`}
                onClick={() => setActiveInput(input.id)}
              >
                {input.label}
              </button>
            ))}
            <button className="tab ghost">Custom</button>
          </div>
          <div className="input-box">
            <input
              value={activeDraft?.value ?? currentInput.value}
              onChange={(event) => handleInputChange(event.target.value)}
              spellCheck={false}
            />
          </div>

          <div className="viz">
            {arrayHeap && <ArrayView array={arrayHeap} focus={currentStep?.focus} locals={currentStep?.locals} />}
            {listHeap && (
              <div>
                <div className="viz-title">Linked List</div>
                <LinkedListView list={listHeap} nodes={currentStep?.heap ?? []} focus={currentStep?.focus} />
              </div>
            )}
            {treeRoot && (
              <div>
                <div className="viz-title">Binary Tree</div>
                <TreeView rootRef={treeRoot.ref} nodes={currentStep?.heap ?? []} focus={currentStep?.focus} />
              </div>
            )}
            {graphHeap && (
              <div>
                <div className="viz-title">Graph</div>
                <GraphView graph={graphHeap} focus={currentStep?.focus} />
              </div>
            )}
            <div className="locals">
              {Object.entries(currentStep?.locals ?? {}).map(([key, value]) => {
                let displayValue = String(value)
                if (typeof value === 'object' && value !== null && 'ref' in value) {
                   const refId = (value as HeapRef).ref
                   const heapObj = currentStep?.heap.find((obj) => obj.ref === refId)
                   if (heapObj) {
                     if (heapObj.kind === 'array') {
                       displayValue = `[${heapObj.items.join(', ')}]`
                     } else if (heapObj.kind === 'list') {
                       displayValue = `List(${refId.split('-')[1]})` // Simplified for now
                     } else {
                       displayValue = refId
                     }
                   } else {
                     displayValue = refId
                   }
                }
                
                return (
                  <div className="local" key={key}>
                    <span className="local-key">{key}</span>
                    <span className="local-value" title={displayValue}>{displayValue}</span>
                  </div>
                )
              })}
            </div>
          </div>
        </section>
      </div>

      <footer className="timeline">
        <div className="controls">
          <button onClick={() => setIsPlaying((prev) => !prev)}>{isPlaying ? 'Pause' : 'Play'}</button>
          <button onClick={() => setStepIndex((prev) => Math.max(prev - 1, 0))}>Prev</button>
          <button onClick={() => setStepIndex((prev) => Math.min(prev + 1, trace.length - 1))}>Next</button>
        </div>
        <input
          className="slider"
          type="range"
          min={0}
          max={Math.max(trace.length - 1, 0)}
          value={stepIndex}
          onChange={(event) => setStepIndex(Number(event.target.value))}
        />
        <div className="timeline-meta">
          <span>
            {trace.length === 0 ? 0 : stepIndex + 1}/{trace.length}
          </span>
          <select value={speed} onChange={(event) => setSpeed(Number(event.target.value))}>
            {SPEEDS.map((option) => (
              <option key={option} value={option}>
                {option}x
              </option>
            ))}
          </select>
        </div>
      </footer>
    </div>
  )
}
