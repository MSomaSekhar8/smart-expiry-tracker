import { MoreHorizontal, Pencil, Trash2, Trash } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { ItemWithStatus } from '@/lib/types'

interface ItemActionsProps {
  item: ItemWithStatus
  onEdit: (item: ItemWithStatus) => void
  onDelete: (item: ItemWithStatus) => void
  onWaste: (item: ItemWithStatus) => void
}

export function ItemActions({ item, onEdit, onDelete, onWaste }: ItemActionsProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="h-8 w-8" aria-label={`Actions for ${item.name}`}>
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => onEdit(item)}>
          <Pencil className="h-4 w-4" />
          Edit
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => onWaste(item)}>
          <Trash className="h-4 w-4" />
          Mark as wasted
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => onDelete(item)}>
          <Trash2 className="h-4 w-4" />
          Delete
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}