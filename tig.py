import csv
import shutil
import sys
import time
import json
import uuid
from pathlib import Path
from glob import glob
from hashlib import sha256
import os
import difflib

HASH_LEN = 16



BASE_DIR = Path(__file__).parent
REPO_DIR = BASE_DIR / 'repo'
BACKUP_DIR = REPO_DIR / '.tig'
SOURCE_DIR = REPO_DIR

STAGE_FILE = BACKUP_DIR / "staged_state.json"
COMMIT_FILE = BACKUP_DIR / "commits.json"

def hash_all(root):
    """
    Create hash values for all files in the repository.
    """
    result = []
    for name in glob(str(root / "**/*.*"), recursive=True):
        full_name = Path(name)
        with open(full_name, "rb") as reader:
            data = reader.read()
            hash_code = sha256(data).hexdigest()[:HASH_LEN]
            result.append((str(full_name.relative_to(root)), hash_code))
    return result

def copy_files(source_dir, backup_dir, manifest):
    """
    Copy files to the backup (.tig) directory using their hash codes as filenames.
    """
    for (filename, hash_code) in manifest:
        source_path = Path(source_dir, filename)
        backup_path = Path(backup_dir, f"{hash_code}.bck")
        if not backup_path.exists():
            shutil.copy(source_path, backup_path)

def current_time():
    return f"{time.time()}".split(".")[0]

def write_manifest(backup_dir, timestamp, manifest):
    """
    Write a manifest file listing the files and their hashes.
    """
    backup_dir = create_dir(backup_dir)
    manifest_file = Path(backup_dir, f"{timestamp}.csv")
    with open(manifest_file, "w") as raw:
        writer = csv.writer(raw)
        writer.writerow(["filename", "hash"])
        writer.writerows(manifest)

def create_dir(dir_path):
    """
    Create a directory if it does not exist.
    """
    backup_dir = Path(dir_path)
    if not backup_dir.exists():
        backup_dir.mkdir()
    return backup_dir

def save_staged_state(staged_state):
    """
    Save the current staged state to a JSON file in the repo directory.
    """
    with open(REPO_DIR / STAGE_FILE, "w") as file:
        json.dump(staged_state, file)

def load_staged_state():
    """
    Load the current staged state from a JSON file in the repo directory.
    """
    stage_file_path = REPO_DIR / STAGE_FILE
    if stage_file_path.exists():
        with open(stage_file_path, "r") as file:
            staged = json.load(file)
        staged = [tuple(item) for item in staged]
        return staged
    else:
        return []

def add_file_to_stage(file_path):
    """
    Add a file to the staging area.
    """
    untracked_file = Path(REPO_DIR, file_path)
    assert untracked_file.exists(), f'Error: the file: {untracked_file} does not exist in this folder'

    with open(untracked_file, "rb") as reader:
        data = reader.read()
        hash_code = sha256(data).hexdigest()[:HASH_LEN]

    staged_state = load_staged_state()
    for i, (staged_file, hash_val) in enumerate(staged_state):
        if staged_file == file_path:
            if hash_val == hash_code:
                print(f'the file {untracked_file} is already in the staged state')
                return
            else:
                print(f'the file {staged_file} was updated in the staged state')
                staged_state[i] = (file_path, hash_code)
                save_staged_state(staged_state)
                return

    staged_state.append((file_path, hash_code))
    print(f'the file {file_path} was added to the staged state')

    save_staged_state(staged_state)

def commit(commit_msg):
    """
    Commit the current staged files, saving them to the .tig directory.
    """
    commit_id = str(uuid.uuid4())[:HASH_LEN]
    commit_date = time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime(time.time()))
    timestamp = current_time()

    staged_state = load_staged_state()
    assert staged_state, "No files to commit. Nothing to change"

    for file_path, hash_code in staged_state:
        source_path = REPO_DIR / file_path
        dest_path = BACKUP_DIR / f"{hash_code}.bck"
        shutil.copy(source_path, dest_path)

    write_manifest(BACKUP_DIR, timestamp, staged_state)

    commits_file = REPO_DIR / COMMIT_FILE
    commits = []
    if commits_file.exists():
        with open(commits_file, "r") as file:
            commits = json.load(file)

    new_commit = {
        "id": commit_id,
        "date": commit_date,
        "message": commit_msg,
        "files": {file: hash for file, hash in staged_state},
        "timestamp": timestamp
    }
    commits.append(new_commit)

    with open(commits_file, "w") as file:
        json.dump(commits, file, indent=4)

    save_staged_state([])

    print(f"Commit '{commit_msg}' is completed with ID {commit_id}")

def log(num=5):
    """
    Display the log of previous commits.
    """
    commits_file = REPO_DIR / COMMIT_FILE
    with open(commits_file, "r") as file:
        commits = json.load(file)

        print("\nCommit History:\n")
        for i, commit in enumerate(reversed(commits)):
            if i == num:
                break
            print(f"{i + 1}. Commit ID: {commit['id']}")
            print(f"   Date: {commit['date']}")
            print(f"   Message: {commit['message']}\n")

def get_status():
    """
    Get the status of all files in the repo directory.
    """
    all_files = hash_all(SOURCE_DIR)
    staged_files = load_staged_state()
    excluded_files = {STAGE_FILE, COMMIT_FILE}
    #staged_files_dict = {file: hash for file, hash in staged_files}
    
    try:
        with open(COMMIT_FILE, 'r') as file:
            commit_data = json.load(file)
        
        commited_files = {}
        for commit in commit_data:
            for file in commit["files"]:
                commited_files[file] = commit["files"][file]
    except:
        commited_files = {}


    for file, hash in all_files:
        if file in excluded_files:
            continue
    
        if file in commited_files and hash == commited_files[file]:
            print(f"The file '{file}' is commited.")
        elif (file, hash) in staged_files:
            print(f"The file '{file}' is staged.")
        elif file in commited_files and hash != commited_files[file]:
            print(f"The file '{file}' is modified and not staged.")
        else:
            print(f"The file '{file}' is untracked.")

def checkout(commit_id):
    """
    Checkout a commit by restoring all files to their committed state.
    """
    commits_file = REPO_DIR / COMMIT_FILE
    assert commits_file.exists(), "Error: No commits found."

    with open(commits_file, "r", newline="") as file:
        commits = json.load(file)

    commit = next((c for c in commits if c["id"] == commit_id), None)
    assert commit, f"Error: Commit ID {commit_id} not found."

    for filename, hash_value in commit["files"].items():
        committed_file_path = BACKUP_DIR / f"{hash_value}.bck"
        assert committed_file_path.exists(), f"Error: Committed file {filename} not found."

        with open(committed_file_path, "r",newline="") as src, open(REPO_DIR / filename, "w",newline="") as dest:
            dest.write(src.read())

    print(f"Checked out commit {commit_id}. Files restored to the working directory.")

def diff(filename):
    """
    Show the differences between the current file and the last committed version.
    """
    manifest_files = sorted(BACKUP_DIR.glob("*.csv"), reverse=True)  
    assert manifest_files, "Error: No commits found. Commit a file first."

    latest_manifest = manifest_files[0]
    committed_hash = None

    with open(latest_manifest, "r", newline="") as manifest:
        reader = csv.DictReader(manifest)
        for row in reader:
            if row["filename"] == filename:
                committed_hash = row["hash"]
                break

    assert committed_hash, f"Error: File {filename} not found in the latest commit."

    committed_file_path = BACKUP_DIR / f"{committed_hash}.bck"
    assert committed_file_path.exists(), f"Error: Committed version of {filename} not found."

    with open(committed_file_path, "r", newline="", encoding="utf-16") as committed_file, open(REPO_DIR / filename, "r", newline="", encoding="utf-16") as current_file:
        committed_lines = committed_file.readlines()
        current_lines = current_file.readlines()

    diff_result = difflib.unified_diff(
        committed_lines,
        current_lines,
        fromfile=f"Committed: {filename}",
        tofile=f"Working: {filename}",
        lineterm=""
    )
    
    
    result = "\n".join(diff_result)
    if not result:
        result = "There is no difference in the file and the previous version."
    print(result)

if __name__ == "__main__":
    if not REPO_DIR.exists():
        print("Error: Repository folder 'repo' does not exist. Please create it first.")
        sys.exit(1)

    command = sys.argv[1]

    if command == 'init':
        create_dir(BACKUP_DIR)
        print(f"Initialized empty repository in {BACKUP_DIR}")
    elif command == 'add':
        assert len(sys.argv) > 2, "Usage: tig.py add <file_path>"
        add_file_to_stage(sys.argv[2])
    elif command == 'commit':
        assert len(sys.argv) > 2, "Usage: tig.py commit <message>"
        commit(sys.argv[2])
    elif command == 'log':
        num = 5 if len(sys.argv) == 2 else int(sys.argv[2].split('-')[-1])
        log(num)
    elif command == 'status':
        get_status()
    elif command == 'checkout':
        assert len(sys.argv) > 2, "Usage: tig.py checkout <commit_id>"
        checkout(sys.argv[2])
    elif command == 'diff':
        assert len(sys.argv) > 2, "Usage: tig.py diff <filename>"
        diff(sys.argv[2])