/**
 * 大列表通用分页配置（antd Table 与 List 通用）。
 * - 默认每页 20 条；
 * - 数据不超过一页时隐藏分页器，小列表保持清爽；
 * - 支持切换每页 10 / 20 / 50 / 100 条，并显示总条数。
 *
 * 不标注具体类型：Table 用 TablePaginationConfig、List 用 PaginationConfig，
 * 对象字面量推断类型可同时赋给两者。
 */
export const LIST_PAGINATION = {
  pageSize: 20,
  hideOnSinglePage: true,
  showSizeChanger: true,
  pageSizeOptions: [10, 20, 50, 100],
  showTotal: (total: number) => `共 ${total} 条`,
};
