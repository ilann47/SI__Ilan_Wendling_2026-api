import { type ReactNode } from 'react';
import { Avatar, Card, CardContent, Stack, Typography } from '@mui/material';

interface Props {
  title: string;
  value: ReactNode;
  subtitle?: ReactNode;
  icon: ReactNode;
  color?: string;
}

export function KpiCard({ title, value, subtitle, icon, color = 'primary.main' }: Props) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Stack spacing={0.5}>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ textTransform: 'uppercase', letterSpacing: 0.5, fontWeight: 700 }}
            >
              {title}
            </Typography>
            <Typography variant="h4">{value}</Typography>
            {subtitle && (
              <Typography variant="body2" color="text.secondary">
                {subtitle}
              </Typography>
            )}
          </Stack>
          <Avatar variant="rounded" sx={{ bgcolor: color, width: 48, height: 48 }}>
            {icon}
          </Avatar>
        </Stack>
      </CardContent>
    </Card>
  );
}
