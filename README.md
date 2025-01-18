# Extended Tig Implementation

## Overview

This repository implements the tig version control system using python and java.

## Part 1 Documentation

In this section the python implementation of the tig version control system is explained in detail. In this section we extend the file archiver implementation from chapter 10 of the course book. In our version of the tig system We make sure to only save the contents of a file if it has been changed. 

## Functions

The `hash_all` function takes a repository path as input. It uses the glob library to go through the root repository and iterate through all files in it. The function outputs a list of tuples that contain the name of each file and its corresponding sha256 value. This function is used in the `get_status` function which reports on the status of the different files in the repository. Using the `hash_all` function `get_status` is able to classify each file into the correct status.

The `copy_files` function uses the shutil library to copy files from the root directory to the backup (.tig) directory using their hash codes as filenames. This function relies on the manifest file which is generated as part of the commit process. The manifest file lists all staged files and their hash. It is used to create the backup and construct commit files

The `current_time` uses the time library to return the current time. It is used in commit and write_manifest to create a unique manifest file name which is timestamp dependent.

The `write_manifest` function creates and writes the manifest in the backup directory. Each row in the manifest consists of the commited filename and its corresponding hash. The filename is constructed from the timestamp at which the commit was made.

The `create_dir` function creates the backup directory if it doesnt exist already. This function is used in combination with the  tig.py init repo command to initialize a tig repository and start tracking files.

The `save_staged_state` function saves the current state of the staged files as a json in the repository. This json file is used to keep track of the state of the tig repo in between commits.

The `load_staged_state` function is a simple function that reads the current state of staged changes from json, parses it so that they are returned as a list of tuples and outputs that result. This function is used in many important points of the implementation, such as adding a file to staged files, commiting, and getting the current status.

The `add_file_to_stage` function adds a file to the staging area by computing its hash and updating the staged state accordingly. It  calculates the file hash using SHA256. If the file is already in the staged state with the same hash, it skips it and if the file has changed, it updates the hash in the staged state. Finally, it saves the updated staged state.

The `commit` function creates a new commit by saving the currently staged files into the repository’s .tig directory and by creating the commit log file. For each staged file, it copies the file to a backup directory and writes the manifest for tracking. After completing the commit process, it clears the staging area and prints a confirmation message with the commit ID.

The `log` function uses the commit file created through the `commit` function to print out a log what what has happened to the repo.

The `get_status` function uses the commit file and the state of the files in the current repo as well as the staging area file to calculate the status and classify each file in the repo into one of 4 categories. For instance if the hash of a file in the current repo doesnt match the lastest hash in the commit file then the file has been changed since the last commit and it categorized as modified.

The `checkout` function restores the files in the repo to the version from a cetrain specified commit. The files in the current repo are overridden by the specific backup files that are kept in the .tig folder.

The `diff` function shows the differences between the current file and the last committed version. It compares the file in the current repo to the backup from the last commited file. If they are the same then the message the "there is no difference is shown".

## Usage

#### 1. Create a directory and initialize a tig repository:

```
mkdir repo 
python tig.py init repo
```
Terminal Output:
```
Initialized empty repository in /path/to/local/folder/soco_group_36/SoCo_HS24-group_36-a3/repo/.tig
```

#### 2. Create two new files in the repository. Their status should be untracked:
```
cd repo
echo "Initial content" > file.txt
echo "Initial content of the other file" > other_file.txt
python ../tig.py status
```
Terminal Output:
```
The file 'file.txt' is untracked.
The file 'other_file.txt' is untracked.
```

#### 3. Start tracking the files. Their status should be staged:
```
python ../tig.py add file.txt
python ../tig.py add other_file.txt
python ../tig.py status
```

Terminal Output:
```
the file file.txt was added to the staged state
the file other_file.txt was added to the staged state

The file 'file.txt' is staged.
The file 'other_file.txt' is staged.
```
#### 4. Commit the files. Their status should be committed:

```
python ../tig.py commit "Initial commit"
python ../tig.py status
```

Terminal Output:
```
Commit 'Initial commit' is completed with ID 5fffab15-2bfb-4b

The file 'file.txt' is commited.
The file 'other_file.txt' is commited.
```

#### 5. Modify one file. It’s status should be modified:
```
echo "Updated content" >> file.txt
python ../tig.py status
```

Terminal Output:
```
The file 'file.txt' is modified and not staged.
The file 'other_file.txt' is commited.
```

#### 6. Check the difference since the last commit:
```
python ../tig.py diff file.txt
```

Terminal Output:
```
--- Committed: file.txt
+++ Working: file.txt
@@ -1 +1,2 @@
 Initial content

+Updated content
```

#### 7. Stage and commit the modified file:
```
python ../tig.py add file.txt
python ../tig.py commit "Updated content in file.txt"
```

Terminal Output:
```
the file file.txt was added to the staged state
Commit 'Updated content in file.txt' is completed with ID 93db454d-620f-44
```

#### 8. Reset (checkout) the repo to the first commit, making `file.txt` go back to it’s original content:

```
python ../tig.py log
python ../tig.py checkout <commit_id_of_first_commit>
```

Terminal Output:
```
Commit History:

1. Commit ID: 93db454d-620f-44
   Date: 2024-12-03 10:03:48
   Message: Updated content in file.txt

2. Commit ID: 5fffab15-2bfb-4b
   Date: 2024-12-03 10:03:03
   Message: Initial commit

Checked out commit 5fffab15-2bfb-4b. Files restored to the working directory.
```

#### 9. Verify that `file.txt` is in the modified state and `other_file.txt` is in the committed state:

```
python ../tig.py status
```

Terminal Output:
```
The file 'file.txt' is modified and not staged.
The file 'other_file.txt' is commited.
```

## Part 2 Documentation

In this section the Java implementation of tig is explained and demonstrated.

## Functions

The most important funcitons contained in the `Tig` class are the following:

* `init(String repoPathStr)`: Initializes a new tig repository at the specified path.
* `add(String fileName)`: Adds a file to staging.
* `commit(String message)`: Commits the changes of files which have been staged with the specified message.
* `status()`: Displays the status of the files.
* `log()`: Displays the commit history of the repository.
* `checkout(String commitId)`: Checks out the specified commit and restores the files to their state at that commit.
* `diff(String fileName)`: Displays the differences between the current file and the version in the last commit.

## Use of Generative AI

Given our somewhat limited experience in Java as a team, for the Java implementation we have heavily relied on Llama3 as well as GPT 3.5. While we do have a basic understand of the Java language, debugging seemed very difficult sometimes, for which these generative models have been helpful (sometimes).

The following prompts were asked, although the majority of the generated responses were not very useful in their original forms, as they often suggested irrelevant or overcomplicated code changes, due to which we rarely could rely on them in their original format and could only gain initial ideas from the response suggestions.

* Correct the path construction to debug below File not found error \< pasted error and code snippet \>

* Adjust the below status method to output Committed status for already staged and committed files \< pasted code snippet \>

* Debug the below Java code so the Committed state is correctly shown for committed files (instead of being untracked) \< pasted code snippet \>

We tried several iterations for this last bug, but with unreliable results.

## Usage

#### 1. Create a directory and initialize a tig repository:

```
mkdir repo_java
java Tig.java init repo_java
```
Terminal Output:
```
Initialized empty repository in /path/to/local/folder/soco_group_36/SoCo_HS24-group_36-a3/repo_java/.tig
Created commits file: /path/to/local/folder/soco_group_36/SoCo_HS24-group_36-a3/repo_java/.tig/commits.csv
```

#### 2. Create two new files in the repository. Their status should be untracked:
```
cd repo_java
echo "Initial content" > file.txt
echo "Initial content of the other file" > other_file.txt
java ../Tig.java status
```
Terminal Output:
```
Untracked: file.txt
Untracked: other_file.txt
```

#### 3. Start tracking the files. Their status should be staged:
```
java ../Tig.java add file.txt
java ../Tig.java add other_file.txt
java ../Tig.java status
```

Terminal Output:
```
File staged: file.txt
File staged: other_file.txt
Staged: file.txt
Staged: other_file.txt
```
#### 4. Commit the files. Their status should be committed:

```
java ../Tig.java commit "Initial commit"
java ../Tig.java status
```

Terminal Output:
```
Committed changes with ID: d52c466c-6b7a-46
Committed: file.txt
Committed: other_file.txt
```

#### 5. Modify one file. It’s status should be modified:
```
echo "Updated content" >> file.txt
java ../Tig.java status
```

Terminal Output:
```
Modified and not staged: file.txt
Committed: other_file.txt
```

#### 6. Check the difference since the last commit:
```
java ../Tig.java diff file.txt
```

Terminal Output:
```
Diff between committed and current version of file.txt:
  Line 1: Initial content
+ Line 2: Updated content
```

#### 7. Stage and commit the modified file:
```
java ../Tig.java add file.txt
java ../Tig.java commit "Updated content in file.txt"
```

Terminal Output:
```
File staged: file.txt
Committed changes with ID: 267383cc-aeba-43
```

#### 8. Reset (checkout) the repo to the first commit, making `file.txt` go back to it’s original content:

```
java ../Tig.java log
java ../Tig.java checkout <commit_id_of_first_commit>
```

Terminal Output:
```
Commit ID: 267383cc-aeba-43
Date: 2024-12-03T14:07:38.524627Z
Message: Updated content in file.txt


Commit ID: d52c466c-6b7a-46
Date: 2024-12-03T14:07:17.042073Z
Message: Initial commit

Checked out commit: d52c466c-6b7a-46
```

#### 9. Verify that `file.txt` is in the modified state and `other_file.txt` is in the committed state:

```
java ../Tig.java status
```

Terminal Output:
```
Modified and not staged: file.txt
Committed: other_file.txt
```

#### 10. `.tigignore` functionality

Here we demonstrate that we can add a `.tigignore` file to the .tig repo and it will act similar to `.gitignore`:


```
cd ./.tig/
echo "ignored_file.txt" > .tigignore
cd ..
echo "Initial content" > ignored_file.txt
java ../Tig.java add ignored_file.txt
java ../Tig.java status
```


Terminal Output:

```
Error: File is ignored: ignored_file.txt
Modified and not staged: file.txt
Committed: other_file.txt
```
