import os
import json
import chromadb
from sentence_transformers import SentenceTransformer
from groq import Groq
from dotenv import load_dotenv
from framework_rulebook import FRAMEWORK_RULEBOOK

# ==============================
# CONFIG
# ==============================
load_dotenv()
VECTOR_DB_PATH = "./vector_db"
COLLECTION_NAME = "framework_chunks"
RECORDED_ACTIONS_PATH = "../../recorded-actions.json"
OUTPUT_PATH = "../generated-test.java"
TOP_K = 3
MODEL_NAME = "llama-3.3-70b-versatile"
MAX_TOKENS = 2000

# ==============================
# 1️⃣ Load local embedding model
# ==============================
print("Loading embedding model...")
embedding_model = SentenceTransformer("all-MiniLM-L6-v2")

# ==============================
# 2️⃣ Load persistent vector DB
# ==============================
print("Loading vector database...")
client = chromadb.PersistentClient(path=VECTOR_DB_PATH)
collection = client.get_collection(name=COLLECTION_NAME)

# ==============================
# 3️⃣ Setup Groq client
# ==============================
api_key = os.getenv("GROQ_API_KEY")
if not api_key:
    raise ValueError("GROQ_API_KEY not found.")

groq_client = Groq(api_key=api_key)

# ==============================
# 4️⃣ Read recorded actions
# ==============================
print("Reading recorded actions...")
with open(RECORDED_ACTIONS_PATH, "r") as f:
    recorded_actions = json.load(f)

print(f"Total actions recorded: {len(recorded_actions)}")

# ==============================
# 5️⃣ Per-action retrieval
# ==============================
print("\nRetrieving framework chunks per action...")

all_chunks = {}  # use dict to avoid duplicate chunks

for action in recorded_actions:
    action_type = action.get("action", "")
    element_id = action.get("id", action.get("name", ""))
    value = action.get("value", "")
    text = action.get("text", "")

    # Build specific query per action
    if action_type == "input":
        query = f"enter input sendKeys {element_id} {value}"
    elif action_type == "click":
        query = f"click button {element_id} {text}"
    else:
        query = f"{action_type} {element_id}"

    print(f"  [{action_type}] {element_id} → query: {query}")

    query_embedding = embedding_model.encode(query).tolist()
    results = collection.query(
        query_embeddings=[query_embedding],
        n_results=TOP_K
    )

    chunks = results.get("documents", [[]])[0]
    metadatas = results.get("metadatas", [[]])[0]

    for chunk, meta in zip(chunks, metadatas):
        key = meta.get("file", "") + chunk[:50]
        if key not in all_chunks:
            all_chunks[key] = (chunk, meta)

print(f"\nTotal unique chunks retrieved: {len(all_chunks)}")

context = "\n\n".join(
    f"[Source: {meta.get('file')}]\n{chunk}"
    for chunk, meta in all_chunks.values()
)

# ==============================
# 6️⃣ Build prompt
# ==============================
prompt = f"""
{FRAMEWORK_RULEBOOK}

Available Framework Methods (from RAG):
{context}

Recorded User Actions (convert these to test steps):
{json.dumps(recorded_actions, indent=2)}

Generate the TestNG test class and JSON test data now. Follow the rulebook strictly.
"""

# ==============================
# 7️⃣ Call Groq
# ==============================
print("Calling Groq LLM...")
response = groq_client.chat.completions.create(
    model=MODEL_NAME,
    messages=[
        {"role": "system", "content": "You are a senior SDET who writes clean Java Selenium TestNG tests."},
        {"role": "user", "content": prompt}
    ],
    temperature=0.1,
    max_tokens=MAX_TOKENS
)

answer = response.choices[0].message.content

# ==============================
# 8️⃣ Parse and save files
# ==============================
import re

def save_generated_files(output: str, base_path: str):
    pattern = r'###\s+([\w\-\.]+)\n```[\w]*\n(.*?)```'
    matches = re.findall(pattern, output, re.DOTALL)

    for filename, content in matches:
        content = content.strip()

        if filename.endswith(".java"):
            package_match = re.search(r'package\s+([\w.]+)', content)
            if package_match:
                package = package_match.group(1)
                package_path = package.replace(".", "/")
                if "pages" in package_path:
                    dest = os.path.join(base_path, "src/main/java", package_path, filename)
                else:
                    dest = os.path.join(base_path, "src/test/java", package_path, filename)
            else:
                dest = os.path.join(base_path, filename)

        elif filename.endswith(".json"):
            dest = os.path.join(base_path, "src/test/resources/test-data", filename)

        elif filename.endswith(".xml"):
            dest = os.path.join(base_path, "src/test/resources/test-suites", filename)

        else:
            dest = os.path.join(base_path, filename)

        os.makedirs(os.path.dirname(dest), exist_ok=True)

        with open(dest, "w") as f:
            f.write(content)

        print(f"✅ [saved] {filename} → {dest}")

    print(f"\n✅ Total files saved: {len(matches)}")

print("\n" + "="*70)
print("✅ GENERATED TEST")
print("="*70)
print(answer)

save_generated_files(answer, "../")