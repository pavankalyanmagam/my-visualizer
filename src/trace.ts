export type HeapRef = { ref: string }

export type HeapArray = {
  kind: 'array'
  ref: string
  name?: string
  items: number[]
}

export type HeapList = {
  kind: 'list'
  ref: string
  name?: string
  head: string
}

export type HeapNode = {
  kind: 'node'
  ref: string
  value: string | number
  next?: string
  left?: string
  right?: string
}

export type HeapGraph = {
  kind: 'graph'
  ref: string
  name?: string
  nodes: { id: string; label: string }[]
  edges: { from: string; to: string }[]
}

export type HeapObject = HeapArray | HeapList | HeapNode | HeapGraph

export type TraceStep = {
  line: number
  locals: Record<string, number | HeapRef>
  heap: HeapObject[]
  focus?: {
    array?: string
    indices?: Record<string, number>
    refs?: string[]
  }
}

export type TraceFile = {
  title: string
  language: 'Java' | 'Python'
  code: string
  inputs: {
    id: string
    label: string
    value: string
    trace: TraceStep[]
  }[]
}
