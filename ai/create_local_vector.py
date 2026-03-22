import os
import re
import chromadb
from dotenv import load_dotenv
from sentence_transformers import SentenceTransformer

# ============================================================
# CONFIGURATION
# ============================================================
load_dotenv()
FRAMEWORK_PATH = os.getenv("FRAMEWORK_PATH")
VECTOR_DB_PATH = "./vector_db"
COLLECTION_NAME = "framework_chunks"
EXCLUDED_DIRS = ["target", "test-output", ".idea", ".git"]

# ============================================================
# 1️⃣ Load Local Embedding Model
# ============================================================
print("🔄 Loading embedding model...")
embedding_model = SentenceTransformer("all-MiniLM-L6-v2")

# ============================================================
# 2️⃣ Scan & Chunk Java Source Files
# ============================================================
print("🔍 Scanning framework for Java files...")

# First pass — read all files
all_files = {}
for root, dirs, files in os.walk(FRAMEWORK_PATH):
    dirs[:] = [d for d in dirs if d not in EXCLUDED_DIRS]
    for file in files:
        if not file.endswith(".java"):
            continue
        full_path = os.path.join(root, file)
        with open(full_path, "r", encoding="utf-8") as f:
            all_files[file] = {"path": full_path, "content": f.read()}

# ============================================================
# Build usage map — which test calls which page method
# ============================================================
# Example: {"login": "loginPage.login(testData.username(), testData.password())"}
usage_map = {}
for file, data in all_files.items():
    if "Test" not in file:
        continue
    content = data["content"]
    # find all page method calls like loginPage.xxx(...) or bookStorePage.xxx(...)
    calls = re.findall(r'\w+Page\.\w+\([^)]*\)', content)
    for call in calls:
        method_name = re.search(r'\.(\w+)\(', call)
        if method_name:
            usage_map[method_name.group(1)] = call

# ============================================================
# Second pass — chunk with relationship context
# ============================================================
chunks_data = []

for file, data in all_files.items():
    content = data["content"]
    full_path = data["path"]

    # Extract class name
    class_match = re.search(r'class\s+(\w+)', content)
    class_name = class_match.group(1) if class_match else "UnknownClass"

    # Extract extends
    extends_match = re.search(r'extends\s+(\w+)', content)
    extends = extends_match.group(1) if extends_match else ""

    # Extract package
    package_match = re.search(r'package\s+([\w.]+)', content)
    package = package_match.group(1) if package_match else ""

    # Extract annotations used in file
    annotations = re.findall(r'@\w+', content)
    unique_annotations = list(set(annotations))

    # Split by method
    parts = content.split("public ")
    for i, part in enumerate(parts):
        if i == 0:
            continue

        method_chunk = "public " + part

        # Extract method name
        method_name_match = re.search(r'(?:void|boolean|String|int|Object|\w+)\s+(\w+)\s*\(', method_chunk)
        method_name = method_name_match.group(1) if method_name_match else ""

        # Find how this method is called in tests
        usage = usage_map.get(method_name, "Not directly called in tests")

        chunk_text = f"""
File: {file}
Package: {package}
Class: {class_name}
Extends: {extends}
Annotations used in class: {', '.join(unique_annotations)}
Method: {method_name}
Called in tests as: {usage}

Code:
public {part}
"""

        metadata = {
            "file": file,
            "class": class_name,
            "package": package,
            "method": method_name,
            "extends": extends,
            "path": full_path
        }

        chunks_data.append((chunk_text, metadata))

print(f"✅ Total chunks created: {len(chunks_data)}")

# ============================================================
# 3️⃣ Reset Vector DB
# ============================================================
print("🗂 Setting up vector database...")
client = chromadb.PersistentClient(path=VECTOR_DB_PATH)

# Delete old collection and recreate fresh
try:
    client.delete_collection(name=COLLECTION_NAME)
    print("🗑 Old collection deleted.")
except:
    pass

collection = client.create_collection(name=COLLECTION_NAME)

# ============================================================
# 4️⃣ Generate Embeddings & Store
# ============================================================
print("⚡ Generating embeddings and storing...")
for i, (chunk_text, metadata) in enumerate(chunks_data):
    embedding = embedding_model.encode(chunk_text).tolist()
    collection.add(
        documents=[chunk_text],
        embeddings=[embedding],
        ids=[f"chunk_{i}"],
        metadatas=[metadata]
    )

print("🚀 Done! Embeddings stored with relationship context.")