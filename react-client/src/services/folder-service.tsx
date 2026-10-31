/*
 * This file is part of Universal Media Server, based on PS3 Media Server.
 *
 * This program is a free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; version 2 of the License only.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
// <custom:folders-root> Whole file: API of the "Folder management" page
import axios from 'axios'
import { TreeNodeData } from '@mantine/core'

import { foldersApiUrl } from '../utils'

/** Color of the selected folder and of the played marks (tree count, file list icon) */
export const accentColor = 'blue'

export interface FolderFile {
  path: string
  name: string
  /** name with the playback label, as the renderers show it */
  title: string
  size: number
  /** seconds, 0 if not scanned yet */
  duration: number
  codec: string
  /** "1920x1080", "" for audio files or if unknown */
  resolution: string
  played: boolean
}

/** nodeProps of a folder tree node: media files and played ones, subfolders included */
export interface FolderCounts {
  files: number
  played: number
  /** bytes of the audio / video files */
  size: number
  /** a virtual folder of the shared content (value "virtual:Name") */
  virtual?: boolean
  /** a shared folder itself (a root, or a folder of a virtual folder): never deleted as a folder */
  shared?: boolean
}

/** Tree node value of a virtual folder: this prefix + its name */
const virtualPrefix = 'virtual:'

/** Title of the files panel: the path, or the name of a virtual folder */
export const getFolderTitle = (value: string): string => {
  return value.startsWith(virtualPrefix) ? value.substring(virtualPrefix.length) : value
}

export interface DeleteResult {
  deleted: number
  /** bytes */
  freed: number
  failed: string[]
  /** moved to the Recycle Bin (use_recycle_bin), not deleted for good */
  recycled: boolean
  /** why files were refused: Link, NotFixedDrive, RecycleBinOff, TooLargeForRecycleBin */
  reason?: string
}

/** Custom Settings that change the page: allow_disk_operations, use_recycle_bin */
export interface FolderOptions {
  allowDiskOperations: boolean
  useRecycleBin: boolean
}

export const getFolderOptions = async (): Promise<FolderOptions> => {
  const response = await axios.get<FolderOptions>(foldersApiUrl + 'options')
  return response.data
}

/**
 * merge: the folders of a virtual folder together, otherwise one node per folder inside it;
 * filter (trimmed): only folders with files whose name has all the words, counting just those files
 */
export const getFolderTree = async (merge: boolean, filter?: string): Promise<TreeNodeData[]> => {
  const response = await axios.get<TreeNodeData[]>(foldersApiUrl + 'tree', { params: { merge, filter: filter || undefined } })
  return response.data
}

export const getFolderFiles = async (path: string): Promise<FolderFile[]> => {
  const response = await axios.post<FolderFile[]>(foldersApiUrl + 'files', { path })
  return response.data
}

/** count: marked; skipped: in shared folders without "monitored" (UMS stores no status there) */
export interface MarkResult {
  count: number
  skipped: number
}

/** The media info reading job: running, files read, files of the job */
export interface ReadStatus {
  running: boolean
  done: number
  total: number
}

/** Starts reading the media info (duration, codecs, video size) of the folder's files; a running job is kept */
export const startReadMediaInfo = async (path: string): Promise<ReadStatus> => {
  const response = await axios.post<ReadStatus>(foldersApiUrl + 'read', { path })
  return response.data
}

export const getReadStatus = async (): Promise<ReadStatus> => {
  const response = await axios.get<ReadStatus>(foldersApiUrl + 'read')
  return response.data
}

export const markFiles = async (paths: string[], played: boolean): Promise<MarkResult> => {
  const response = await axios.post<MarkResult>(foldersApiUrl + 'mark', { paths, played })
  return response.data
}

/** recycle: the mode the page warned about; the server answers 409 when the setting differs */
export const deleteFiles = async (paths: string[], recycle: boolean): Promise<DeleteResult> => {
  const response = await axios.post<DeleteResult>(foldersApiUrl + 'delete', { paths, recycle })
  return response.data
}

export interface DeleteFolderResult extends DeleteResult {
  /** the folder is gone (false: some files could not be deleted and stayed) */
  removed: boolean
}

/**
 * The tree node of a folder that may be deleted from the disk: a real folder below a shared folder,
 * not a shared folder itself (the server marks those "shared") nor a virtual folder
 */
export const findDeletableFolder = (nodes: TreeNodeData[], path: string): TreeNodeData | undefined => {
  const node = path.startsWith(virtualPrefix) ? undefined : findNode(nodes, path)
  return node && !(node.nodeProps as FolderCounts | undefined)?.shared ? node : undefined
}

const findNode = (nodes: TreeNodeData[], value: string): TreeNodeData | undefined => {
  for (const node of nodes) {
    const found = node.value === value ? node : findNode(node.children ?? [], value)
    if (found) {
      return found
    }
  }
  return undefined
}

/** Deletes the folder with all its content from the disk (not a shared folder itself) */
export const deleteFolder = async (path: string, recycle: boolean): Promise<DeleteFolderResult> => {
  const response = await axios.post<DeleteFolderResult>(foldersApiUrl + 'delete-folder', { path, recycle })
  return response.data
}

/** Values of the nodes from a root down to the folder, undefined if the folder is not in the tree */
export const findFolderPath = (nodes: TreeNodeData[], path: string): string[] | undefined => {
  for (const node of nodes) {
    if (node.value === path) {
      return [node.value]
    }
    const childPath = node.children ? findFolderPath(node.children, path) : undefined
    if (childPath) {
      return [node.value, ...childPath]
    }
  }
  return undefined
}

/** The folder, or its nearest parent that is still in the tree (folders can be deleted or renamed) */
export const findExistingFolder = (nodes: TreeNodeData[], path: string): string | undefined => {
  let current = path
  while (current) {
    if (findFolderPath(nodes, current)) {
      return current
    }
    const separator = Math.max(current.lastIndexOf('\\'), current.lastIndexOf('/'))
    current = separator > 0 ? current.substring(0, separator) : ''
  }
  return undefined
}

/** Name filter: every word of the filter is in the name, case-insensitive; an empty filter matches all */
export const matchesFilter = (name: string, filter: string): boolean => {
  const lowerName = name.toLowerCase()
  return filter.toLowerCase().split(/\s+/).every(word => lowerName.includes(word))
}

export const formatSize = (bytes: number): string => {
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit++
  }
  return (unit === 0 ? size : size.toFixed(1)) + ' ' + units[unit]
}

/** "h:mm:ss", "mm:ss" under one hour, "" if unknown */
export const formatDuration = (seconds: number): string => {
  if (!seconds) {
    return ''
  }
  const total = Math.round(seconds)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60).toString().padStart(2, '0')
  const secs = (total % 60).toString().padStart(2, '0')
  return hours > 0 ? `${hours}:${minutes}:${secs}` : `${minutes}:${secs}`
}
// </custom:folders-root>
