import { type ReactElement, useMemo, useState } from 'react';
import { Box, Button, Card } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import {
  DataGrid,
  GridActionsCellItem,
  type GridActionsCellItemProps,
  type GridColDef,
} from '@mui/x-data-grid';
import { ptBR } from '@mui/x-data-grid/locales';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { describeError } from '../../api/client';
import { createResourceApi, type PageParams } from '../../api/resource';
import { useSnackbar } from '../SnackbarProvider';
import { ResourceFormDialog } from '../form/ResourceFormDialog';
import { ConfirmDialog } from '../common/ConfirmDialog';
import { PageHeader } from '../common/PageHeader';
import { FilterBar } from './FilterBar';
import { ActionRunner } from './ActionRunner';
import { type ResourceConfig, type RowAction } from './resourceConfig';

const gridLocale = ptBR.components.MuiDataGrid.defaultProps.localeText;

export function CrudResourcePage({ config }: { config: ResourceConfig }) {
  const queryClient = useQueryClient();
  const { notify } = useSnackbar();
  const resource = useMemo(() => createResourceApi<any, any>(config.basePath), [config.basePath]);

  const [pagination, setPagination] = useState({ page: 0, pageSize: 10 });
  const [filters, setFilters] = useState<Record<string, unknown>>({});
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Record<string, any> | null>(null);
  const [deleting, setDeleting] = useState<Record<string, any> | null>(null);
  const [running, setRunning] = useState<{ action: RowAction; row: Record<string, any> } | null>(null);

  const canCreate = config.canCreate !== false;
  const canEdit = config.canEdit !== false;
  const canDelete = config.canDelete !== false;

  const params: PageParams = {
    page: pagination.page,
    size: pagination.pageSize,
    ...(config.defaultSort ? { sort: config.defaultSort } : {}),
    ...filters,
  };

  const { data, isFetching } = useQuery({
    queryKey: ['list', config.basePath, pagination, filters],
    queryFn: () => resource.list(params),
    placeholderData: keepPreviousData,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['list', config.basePath] });

  const saveMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      editing ? resource.update(editing.id, values) : resource.create(values),
    onSuccess: () => {
      notify(`${config.singular} salvo com sucesso.`, 'success');
      setFormOpen(false);
      setEditing(null);
      invalidate();
    },
    onError: (e) => notify(describeError(e), 'error'),
  });

  const deleteMutation = useMutation({
    mutationFn: (row: Record<string, any>) => resource.remove(row.id),
    onSuccess: () => {
      notify(`${config.singular} excluído.`, 'success');
      setDeleting(null);
      invalidate();
    },
    onError: (e) => {
      notify(describeError(e), 'error');
      setDeleting(null);
    },
  });

  const columns = useMemo<GridColDef[]>(() => {
    const actionCount = (config.rowActions?.length ?? 0) + (canEdit ? 1 : 0) + (canDelete ? 1 : 0);
    const actionsCol: GridColDef = {
      field: '__actions',
      type: 'actions',
      headerName: 'Ações',
      width: Math.max(80, 44 + actionCount * 8),
      getActions: (p) => {
        const items: ReactElement<GridActionsCellItemProps>[] = [];
        if (canEdit) {
          items.push(
            <GridActionsCellItem
              key="edit"
              icon={<EditOutlinedIcon />}
              label="Editar"
              onClick={() => {
                setEditing(p.row);
                setFormOpen(true);
              }}
            />,
          );
        }
        (config.rowActions ?? []).forEach((a) => {
          if (a.visible && !a.visible(p.row)) return;
          items.push(
            <GridActionsCellItem
              key={a.key}
              icon={a.icon ?? <span />}
              label={a.label}
              showInMenu
              onClick={() => setRunning({ action: a, row: p.row })}
            />,
          );
        });
        if (canDelete) {
          items.push(
            <GridActionsCellItem
              key="delete"
              icon={<DeleteOutlineIcon />}
              label="Excluir"
              showInMenu
              onClick={() => setDeleting(p.row)}
            />,
          );
        }
        return items;
      },
    };
    return [...config.columns, actionsCol];
  }, [config, canEdit, canDelete]);

  const initialValues = editing ? (config.toFormValues ? config.toFormValues(editing) : editing) : null;

  return (
    <Box>
      <PageHeader
        title={config.plural}
        subtitle={config.subtitle}
        action={
          canCreate ? (
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => {
                setEditing(null);
                setFormOpen(true);
              }}
            >
              Novo
            </Button>
          ) : undefined
        }
      />

      {config.filters && config.filters.length > 0 && (
        <FilterBar filters={config.filters} onChange={(v) => {
          setFilters(v);
          setPagination((p) => ({ ...p, page: 0 }));
        }} />
      )}

      <Card>
        <DataGrid
          autoHeight
          rows={data?.content ?? []}
          columns={columns}
          getRowId={(row) => row.id}
          loading={isFetching}
          localeText={gridLocale}
          rowCount={data?.totalElements ?? 0}
          paginationMode="server"
          paginationModel={pagination}
          onPaginationModelChange={setPagination}
          pageSizeOptions={[10, 25, 50]}
          disableRowSelectionOnClick
          disableColumnMenu
          sx={{ border: 0, '--DataGrid-overlayHeight': '300px' }}
        />
      </Card>

      <ResourceFormDialog
        open={formOpen}
        title={editing ? `Editar ${config.singular}` : `Novo ${config.singular}`}
        fields={config.fields}
        initialValues={initialValues}
        submitting={saveMutation.isPending}
        onClose={() => {
          setFormOpen(false);
          setEditing(null);
        }}
        onSubmit={(values) => saveMutation.mutate(values)}
      />

      <ConfirmDialog
        open={!!deleting}
        title={`Excluir ${config.singular}`}
        message={`Tem certeza que deseja excluir este registro? Esta ação não pode ser desfeita.`}
        confirmLabel="Excluir"
        confirmColor="error"
        loading={deleteMutation.isPending}
        onConfirm={() => deleting && deleteMutation.mutate(deleting)}
        onClose={() => setDeleting(null)}
      />

      {running && (
        <ActionRunner
          basePath={config.basePath}
          action={running.action}
          row={running.row}
          onClose={() => setRunning(null)}
          onDone={() => {
            setRunning(null);
            invalidate();
          }}
        />
      )}
    </Box>
  );
}
