# Java Trace Backend

Runs Java code with JDI and returns a trace JSON the frontend can visualize.

## Run

```bash
cd "/Users/pavankalyanmagam/Documents/New project/code-visualizer/backend"
./run.sh
```

Server starts on `http://localhost:8080` with `POST /run`.

## Request

```json
{
  "title": "Move Zeroes",
  "language": "Java",
  "code": "public class Solution { public void moveZeroes(int[] nums) { ... } }",
  "inputs": [
    { "id": "input-1", "label": "Input 1", "value": "nums = [0,1,0,3,12]" },
    { "id": "input-2", "label": "Input 2", "value": "nums = [1,0,2,0,4,5]" },
    { "id": "input-3", "label": "Input 3", "value": "nums = [0,0,0,1]" }
  ]
}
```

## Notes
- Code can be either:
  - A class with a `main` method (executed directly), or
  - A LeetCode-style class with a single method (auto-wrapped in `Main`).
- The input parser supports primitives and arrays: `int`, `long`, `double`, `boolean`, `String`, and `[]`/`[][]` forms.
- Tracing is line-by-line with a step limit (default 3000 steps).
- Heap capture focuses on arrays and simple `Node`-style structures (`value/val`, `next`, `left`, `right`).

If your signature uses complex types (e.g., `ListNode`, `TreeNode`), we can extend the wrapper to build those from input strings next.
